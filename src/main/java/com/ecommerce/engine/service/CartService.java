package com.ecommerce.engine.service;

import com.ecommerce.engine.dto.cart.AddToCartRequest;
import com.ecommerce.engine.dto.cart.CartItemResponse;
import com.ecommerce.engine.dto.cart.CartResponse;
import com.ecommerce.engine.dto.cart.UpdateCartItemRequest;
import com.ecommerce.engine.entity.AppUser;
import com.ecommerce.engine.entity.Cart;
import com.ecommerce.engine.entity.CartItem;
import com.ecommerce.engine.entity.Product;
import com.ecommerce.engine.exception.BadRequestException;
import com.ecommerce.engine.exception.ResourceNotFoundException;
import com.ecommerce.engine.repository.CartRepository;
import com.ecommerce.engine.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final UserLookupService userLookupService;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public CartResponse getCart(String username) {
        AppUser user = userLookupService.getRequiredUser(username);
        Cart cart = cartRepository.findDetailedByUserId(user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + username));
        return toResponse(cart);
    }

    @Transactional
    public CartResponse addItem(String username, AddToCartRequest request) {
        AppUser user = userLookupService.getRequiredUser(username);
        Cart cart = lockCart(user.getId());
        Product product = productRepository.findById(request.productId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.productId()));

        if (!product.isActive()) {
            throw new BadRequestException("Product is not active");
        }
        if (request.quantity() > product.getInventory().getAvailableQuantity()) {
            throw new BadRequestException("Requested quantity exceeds currently visible stock");
        }

        CartItem item = cart.getItems().stream()
            .filter(existing -> existing.getProduct().getId().equals(product.getId()))
            .findFirst()
            .orElseGet(() -> createCartItem(cart, product));

        int requestedTotalQuantity = item.getQuantity() + request.quantity();
        if (requestedTotalQuantity > product.getInventory().getAvailableQuantity()) {
            throw new BadRequestException("Requested quantity exceeds currently visible stock");
        }

        item.setQuantity(requestedTotalQuantity);
        item.setUnitPriceSnapshot(product.getPrice());
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(String username, Long productId, UpdateCartItemRequest request) {
        AppUser user = userLookupService.getRequiredUser(username);
        Cart cart = lockCart(user.getId());
        CartItem item = cart.getItems().stream()
            .filter(existing -> existing.getProduct().getId().equals(productId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Cart item not found for product: " + productId));

        if (request.quantity() == 0) {
            cart.getItems().remove(item);
        } else {
            if (request.quantity() > item.getProduct().getInventory().getAvailableQuantity()) {
                throw new BadRequestException("Requested quantity exceeds currently visible stock");
            }
            item.setQuantity(request.quantity());
            item.setUnitPriceSnapshot(item.getProduct().getPrice());
        }
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(String username, Long productId) {
        return updateItem(username, productId, new UpdateCartItemRequest(0));
    }

    private Cart lockCart(Long userId) {
        // The cart row is locked before we mutate items so concurrent requests from the same user
        // cannot interleave add/remove/checkout operations and create duplicate or lost updates.
        Cart lockedCart = cartRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user id: " + userId));
        return cartRepository.findById(lockedCart.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user id: " + userId));
    }

    private CartItem createCartItem(Cart cart, Product product) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(0);
        item.setUnitPriceSnapshot(product.getPrice());
        cart.getItems().add(item);
        return item;
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : cart.getItems().stream().sorted(Comparator.comparing(ci -> ci.getProduct().getId())).toList()) {
            BigDecimal lineTotal = item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity()));
            items.add(new CartItemResponse(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getUnitPriceSnapshot(),
                item.getQuantity(),
                lineTotal
            ));
            total = total.add(lineTotal);
        }

        return new CartResponse(cart.getId(), items, total, cart.getUpdatedAt());
    }
}
