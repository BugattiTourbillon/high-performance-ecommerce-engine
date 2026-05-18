package com.ecommerce.engine.controller;

import com.ecommerce.engine.dto.product.ProductResponse;
import com.ecommerce.engine.service.BenchmarkCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/benchmark")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkCatalogService benchmarkCatalogService;

    @GetMapping("/products/raw")
    public List<ProductResponse> listProductsRaw(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return benchmarkCatalogService.listProductsRaw(page, size);
    }

    @GetMapping("/products/popular/raw")
    public List<ProductResponse> popularProductsRaw() {
        return benchmarkCatalogService.listPopularProductsRaw();
    }

    @GetMapping("/products/{productId}/raw")
    public ProductResponse getProductRaw(@PathVariable Long productId) {
        return benchmarkCatalogService.getProductRaw(productId);
    }
}
