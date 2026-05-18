package com.ecommerce.engine.service;

import com.ecommerce.engine.dto.product.ProductResponse;
import com.ecommerce.engine.entity.Product;
import com.ecommerce.engine.exception.ResourceNotFoundException;
import com.ecommerce.engine.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BenchmarkCatalogService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<ProductResponse> listProductsRaw(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findActiveProductsRaw(pageable)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listPopularProductsRaw() {
        return productRepository.findPopularProductsRaw(PageRequest.of(0, 10))
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductRaw(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        return toResponse(product);
    }

    private ProductResponse toResponse(Product product) {
        int availableQuantity = product.getInventory() == null ? 0 : product.getInventory().getAvailableQuantity();
        return new ProductResponse(
            product.getId(),
            product.getSku(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.isActive(),
            availableQuantity,
            product.getPopularityScore(),
            product.getVersion()
        );
    }
}
