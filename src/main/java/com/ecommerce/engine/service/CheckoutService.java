package com.ecommerce.engine.service;

import com.ecommerce.engine.dto.checkout.CheckoutRequest;
import com.ecommerce.engine.dto.order.OrderItemResponse;
import com.ecommerce.engine.dto.order.OrderResponse;
import com.ecommerce.engine.config.CacheNames;
import com.ecommerce.engine.entity.AppUser;
import com.ecommerce.engine.entity.Cart;
import com.ecommerce.engine.entity.CartItem;
import com.ecommerce.engine.entity.CustomerOrder;
import com.ecommerce.engine.entity.Inventory;
import com.ecommerce.engine.entity.OrderItem;
import com.ecommerce.engine.entity.OrderStatus;
import com.ecommerce.engine.entity.PaymentRecord;
import com.ecommerce.engine.event.OrderPlacedEvent;
import com.ecommerce.engine.exception.BadRequestException;
import com.ecommerce.engine.exception.InsufficientStockException;
import com.ecommerce.engine.exception.ResourceNotFoundException;
import com.ecommerce.engine.repository.CartRepository;
import com.ecommerce.engine.repository.CustomerOrderRepository;
import com.ecommerce.engine.repository.InventoryRepository;
import com.ecommerce.engine.repository.PaymentRecordRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final UserLookupService userLookupService;
    private final CartRepository cartRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentGatewaySimulator paymentGatewaySimulator;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    @CacheEvict(value = {CacheNames.CATALOG_PAGE, CacheNames.POPULAR_PRODUCTS, CacheNames.PRODUCT_DETAILS}, allEntries = true)
    public OrderResponse checkout(String username, CheckoutRequest request) {
        AppUser user = userLookupService.getRequiredUser(username);
        Cart lockedCart = cartRepository.findByUserIdForUpdate(user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + username));
        Cart cart = cartRepository.findById(lockedCart.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + username));

        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cannot checkout an empty cart");
        }

        List<CartItem> cartItems = cart.getItems().stream()
            .sorted(Comparator.comparing(item -> item.getProduct().getId()))
            .toList();
        List<Long> productIds = cartItems.stream().map(item -> item.getProduct().getId()).toList();

        // We lock every inventory row in ascending product-id order. That deterministic ordering
        // keeps concurrent overlapping checkouts from deadlocking while still guaranteeing that only
        // one transaction can decrement a given stock row at a time across all application nodes.
        Map<Long, Inventory> lockedInventory = inventoryRepository.findAllByProductIdsForUpdate(productIds)
            .stream()
            .collect(HashMap::new, (map, inventory) -> map.put(inventory.getProduct().getId(), inventory), HashMap::putAll);

        if (lockedInventory.size() != productIds.size()) {
            throw new ResourceNotFoundException("One or more products no longer exist in inventory");
        }

        CustomerOrder order = new CustomerOrder();
        order.setUser(user);
        order.setCustomerEmail(user.getEmail());
        order.setStatus(OrderStatus.PENDING);
        order.setItems(new ArrayList<>());

        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalItems = 0;

        for (CartItem cartItem : cartItems) {
            Inventory inventory = lockedInventory.get(cartItem.getProduct().getId());
            if (inventory.getAvailableQuantity() < cartItem.getQuantity()) {
                throw new InsufficientStockException(
                    "Insufficient stock for product " + cartItem.getProduct().getName() + ". Remaining stock: "
                        + inventory.getAvailableQuantity()
                );
            }

            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - cartItem.getQuantity());
            inventory.getProduct().setPopularityScore(inventory.getProduct().getPopularityScore() + cartItem.getQuantity());

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setProductNameSnapshot(cartItem.getProduct().getName());
            orderItem.setSkuSnapshot(cartItem.getProduct().getSku());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setUnitPriceSnapshot(cartItem.getUnitPriceSnapshot());
            order.getItems().add(orderItem);

            totalItems += cartItem.getQuantity();
            totalAmount = totalAmount.add(cartItem.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        order.setTotalAmount(totalAmount);
        order.setTotalItems(totalItems);

        PaymentSimulationResult paymentResult = paymentGatewaySimulator.authorize(request.paymentToken(), totalAmount);
        order.setStatus(OrderStatus.PAID);
        CustomerOrder savedOrder = customerOrderRepository.save(order);

        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setOrder(savedOrder);
        paymentRecord.setAmount(totalAmount);
        paymentRecord.setStatus(paymentResult.status());
        paymentRecord.setProviderReference(paymentResult.providerReference());
        paymentRecordRepository.save(paymentRecord);
        savedOrder.setPaymentRecord(paymentRecord);

        cart.getItems().clear();

        applicationEventPublisher.publishEvent(new OrderPlacedEvent(savedOrder.getId(), savedOrder.getCustomerEmail()));
        return toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(String username) {
        return customerOrderRepository.findByUserUsernameOrderByCreatedAtDesc(username)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private OrderResponse toResponse(CustomerOrder order) {
        List<OrderItemResponse> items = order.getItems().stream()
            .map(item -> new OrderItemResponse(
                item.getProduct().getId(),
                item.getProductNameSnapshot(),
                item.getSkuSnapshot(),
                item.getQuantity(),
                item.getUnitPriceSnapshot(),
                item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity()))
            ))
            .toList();

        String paymentReference = order.getPaymentRecord() == null ? null : order.getPaymentRecord().getProviderReference();
        return new OrderResponse(
            order.getId(),
            order.getStatus().name(),
            order.getTotalAmount(),
            order.getTotalItems(),
            paymentReference,
            order.getCreatedAt(),
            items
        );
    }
}
