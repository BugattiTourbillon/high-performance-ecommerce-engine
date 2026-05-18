package com.ecommerce.engine.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.engine.entity.AppUser;
import com.ecommerce.engine.entity.Cart;
import com.ecommerce.engine.entity.Inventory;
import com.ecommerce.engine.entity.Product;
import com.ecommerce.engine.entity.RoleName;
import com.ecommerce.engine.repository.AppUserRepository;
import com.ecommerce.engine.repository.CartRepository;
import com.ecommerce.engine.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long seededProductId;

    @BeforeEach
    void setUp() {
        if (!appUserRepository.existsByUsername("admin")) {
            AppUser admin = new AppUser();
            admin.setUsername("admin");
            admin.setEmail("admin@test.local");
            admin.setPasswordHash(passwordEncoder.encode("Admin1234!"));
            admin.setRole(RoleName.ADMIN);
            AppUser savedAdmin = appUserRepository.save(admin);

            Cart cart = new Cart();
            cart.setUser(savedAdmin);
            cartRepository.save(cart);
        }

        Product product = new Product();
        product.setSku("API-SEED-" + System.nanoTime());
        product.setName("API Seed Product");
        product.setDescription("Seed product for endpoint integration tests");
        product.setPrice(new BigDecimal("10.00"));
        product.setActive(true);

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setAvailableQuantity(10);
        product.setInventory(inventory);
        seededProductId = productRepository.save(product).getId();
    }

    @Test
    void shouldExerciseAllHttpEndpointGroups() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/popular"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/{productId}", seededProductId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(seededProductId));

        mockMvc.perform(get("/api/benchmark/products/raw"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/benchmark/products/popular/raw"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/benchmark/products/{productId}/raw", seededProductId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(seededProductId));

        String adminToken = login("admin", "Admin1234!");
        String customerToken = register("api-buyer-" + System.nanoTime(), "api-buyer@example.com", "Secret123!");

        MvcResult createdProduct = mockMvc.perform(post("/api/admin/products")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sku": "API-CREATED-%s",
                      "name": "Created Product",
                      "description": "Created by API integration test",
                      "price": 42.50,
                      "availableQuantity": 15
                    }
                    """.formatted(System.nanoTime())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.availableQuantity").value(15))
            .andReturn();
        Long productId = read(createdProduct).get("id").asLong();

        mockMvc.perform(put("/api/admin/inventory/{productId}", productId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"availableQuantity\":12}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableQuantity").value(12));

        mockMvc.perform(get("/api/cart")
                .header("Authorization", bearer(customerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(post("/api/cart/items")
                .header("Authorization", bearer(customerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":%d,\"quantity\":2}".formatted(productId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)));

        mockMvc.perform(put("/api/cart/items/{productId}", productId)
                .header("Authorization", bearer(customerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].quantity").value(1));

        mockMvc.perform(delete("/api/cart/items/{productId}", productId)
                .header("Authorization", bearer(customerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(post("/api/cart/items")
                .header("Authorization", bearer(customerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":%d,\"quantity\":1}".formatted(productId)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/checkout")
                .header("Authorization", bearer(customerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentToken\":\"tok_api_success\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(get("/api/orders")
                .header("Authorization", bearer(customerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        mockMvc.perform(post("/api/admin/jobs/daily-sales")
                .queryParam("salesDate", today.toString())
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/reports/daily-sales/{salesDate}", today)
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.salesDate").value(today.toString()));

        mockMvc.perform(get("/api/cart"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/products")
                .header("Authorization", bearer(customerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sku": "FORBIDDEN",
                      "name": "Forbidden",
                      "description": "Forbidden",
                      "price": 1,
                      "availableQuantity": 1
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    private String register(String username, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(username, email, password)))
            .andExpect(status().isCreated())
            .andReturn();
        return read(result).get("accessToken").asText();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "password": "%s"
                    }
                    """.formatted(username, password)))
            .andExpect(status().isOk())
            .andReturn();
        return read(result).get("accessToken").asText();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
