package com.ecommerce.engine.service;

import com.ecommerce.engine.dto.product.InventoryAdjustmentRequest;
import com.ecommerce.engine.dto.product.ProductResponse;
import com.ecommerce.engine.config.CacheNames;
import com.ecommerce.engine.entity.Product;
import com.ecommerce.engine.exception.ResourceNotFoundException;
import com.ecommerce.engine.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final ProductCatalogService productCatalogService;

    @Transactional
    @CacheEvict(value = {CacheNames.CATALOG_PAGE, CacheNames.POPULAR_PRODUCTS, CacheNames.PRODUCT_DETAILS}, allEntries = true)
    public ProductResponse updateInventory(Long productId, InventoryAdjustmentRequest request) {
        Product product = productRepository.findByIdForUpdate(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        product.getInventory().setAvailableQuantity(request.availableQuantity());
        return productCatalogService.toResponse(product);
    }
}
