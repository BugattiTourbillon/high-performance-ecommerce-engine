package com.ecommerce.engine.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ecommerce.engine.dto.checkout.CheckoutRequest;
import com.ecommerce.engine.entity.AppUser;
import com.ecommerce.engine.entity.Cart;
import com.ecommerce.engine.entity.CartItem;
import com.ecommerce.engine.entity.Inventory;
import com.ecommerce.engine.entity.Product;
import com.ecommerce.engine.entity.RoleName;
import com.ecommerce.engine.exception.InsufficientStockException;
import com.ecommerce.engine.repository.AppUserRepository;
import com.ecommerce.engine.repository.CartRepository;
import com.ecommerce.engine.repository.CustomerOrderRepository;
import com.ecommerce.engine.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.checkout.max-concurrent-payment-simulations=64")
class ConcurrentCheckoutIntegrationTest {

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerOrderRepository customerOrderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldPreventOversellingUnderConcurrentCheckoutPressure() throws Exception {
        Product product = createProduct("SKU-CONC-1", "Last Unit Console", 10);
        List<String> usernames = seedUsersWithSingleItemCart(product, 20);

        AtomicInteger successfulCheckouts = new AtomicInteger();
        AtomicInteger rejectedCheckouts = new AtomicInteger();
        List<Throwable> unexpectedErrors = java.util.Collections.synchronizedList(new ArrayList<>());
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch ready = new CountDownLatch(usernames.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(usernames.size());

        for (String username : usernames) {
            executorService.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    checkoutService.checkout(username, new CheckoutRequest("tok_" + username));
                    successfulCheckouts.incrementAndGet();
                } catch (InsufficientStockException ex) {
                    rejectedCheckouts.incrementAndGet();
                } catch (Throwable ex) {
                    unexpectedErrors.add(ex);
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS), "Workers did not initialize in time");
        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS), "Workers did not finish in time");
        executorService.shutdownNow();

        Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertTrue(unexpectedErrors.isEmpty(), "Unexpected errors: " + unexpectedErrors);
        assertEquals(10, successfulCheckouts.get(), "Exactly the available stock should be sold");
        assertEquals(10, rejectedCheckouts.get(), "Remaining buyers should be rejected cleanly");
        assertEquals(0, reloadedProduct.getInventory().getAvailableQuantity(), "Inventory must never go negative");
        assertEquals(10, customerOrderRepository.count(), "Only successful checkouts should create orders");
    }

    private Product createProduct(String sku, String name, int stock) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setDescription("Concurrency test product");
        product.setPrice(new BigDecimal("19.99"));
        product.setActive(true);

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setAvailableQuantity(stock);
        product.setInventory(inventory);
        return productRepository.save(product);
    }

    private List<String> seedUsersWithSingleItemCart(Product product, int numberOfUsers) {
        List<String> usernames = new ArrayList<>();
        for (int index = 0; index < numberOfUsers; index++) {
            String username = "buyer-" + index;
            AppUser user = new AppUser();
            user.setUsername(username);
            user.setEmail(username + "@mail.test");
            user.setPasswordHash(passwordEncoder.encode("Secret123!"));
            user.setRole(RoleName.CUSTOMER);
            AppUser savedUser = appUserRepository.save(user);

            Cart cart = new Cart();
            cart.setUser(savedUser);

            CartItem cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProduct(product);
            cartItem.setQuantity(1);
            cartItem.setUnitPriceSnapshot(product.getPrice());
            cart.getItems().add(cartItem);

            cartRepository.save(cart);
            usernames.add(username);
        }
        return usernames;
    }
}
