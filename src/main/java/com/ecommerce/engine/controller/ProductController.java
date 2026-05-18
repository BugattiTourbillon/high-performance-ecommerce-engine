package com.ecommerce.engine.controller;

import com.ecommerce.engine.dto.product.ProductResponse;
import com.ecommerce.engine.service.ProductCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductCatalogService productCatalogService;

    @GetMapping
    public List<ProductResponse> listProducts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return productCatalogService.listProducts(page, size);
    }

    @GetMapping("/popular")
    public List<ProductResponse> popularProducts() {
        return productCatalogService.listPopularProducts();
    }

    @GetMapping("/{productId}")
    public ProductResponse getProduct(@PathVariable Long productId) {
        return productCatalogService.getProduct(productId);
    }
}
