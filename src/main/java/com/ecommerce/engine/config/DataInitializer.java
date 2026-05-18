package com.ecommerce.engine.config;

import com.ecommerce.engine.entity.AppUser;
import com.ecommerce.engine.entity.Cart;
import com.ecommerce.engine.entity.Inventory;
import com.ecommerce.engine.entity.Product;
import com.ecommerce.engine.entity.RoleName;
import com.ecommerce.engine.repository.AppUserRepository;
import com.ecommerce.engine.repository.CartRepository;
import com.ecommerce.engine.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("!test")
@RequiredArgsConstructor
public class DataInitializer {

    private final AppUserRepository appUserRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    ApplicationRunner seedData() {
        return args -> {
            if (!appUserRepository.existsByUsername("admin")) {
                AppUser admin = new AppUser();
                admin.setUsername("admin");
                admin.setEmail("admin@commerce.local");
                admin.setPasswordHash(passwordEncoder.encode("Admin1234!"));
                admin.setRole(RoleName.ADMIN);
                AppUser savedAdmin = appUserRepository.save(admin);

                Cart adminCart = new Cart();
                adminCart.setUser(savedAdmin);
                cartRepository.save(adminCart);
            }

            if (productRepository.count() == 0) {
                productRepository.saveAll(List.of(
                    buildProduct("SKU-001", "Load-Test Laptop", "High throughput laptop for stress scenarios", new BigDecimal("1499.00"), 75),
                    buildProduct("SKU-002", "Concurrent Keyboard", "Mechanical keyboard used for concurrency demos", new BigDecimal("129.00"), 150),
                    buildProduct("SKU-003", "Redis Mouse", "Low-latency wireless mouse", new BigDecimal("79.00"), 200),
                    buildProduct("SKU-004", "Batch Monitor", "Monitoring display for ops dashboards", new BigDecimal("499.00"), 40)
                ));
            }
        };
    }

    private Product buildProduct(String sku, String name, String description, BigDecimal price, int stock) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setActive(true);

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setAvailableQuantity(stock);
        product.setInventory(inventory);
        return product;
    }
}
