package com.ecommerce.engine.service;

import com.ecommerce.engine.dto.product.ProductCreateRequest;
import com.ecommerce.engine.dto.product.ProductResponse;
import com.ecommerce.engine.config.CacheNames;
import com.ecommerce.engine.entity.Inventory;
import com.ecommerce.engine.entity.Product;
import com.ecommerce.engine.exception.DuplicateResourceException;
import com.ecommerce.engine.exception.ResourceNotFoundException;
import com.ecommerce.engine.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductCatalogService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.CATALOG_PAGE, key = "#page + ':' + #size")
    public List<ProductResponse> listProducts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findByActiveTrue(pageable)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.POPULAR_PRODUCTS, key = "'top-10'")
    public List<ProductResponse> listPopularProducts() {
        return productRepository.findPopularProducts(PageRequest.of(0, 10))
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.PRODUCT_DETAILS, key = "#productId")
    public ProductResponse getProduct(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        return toResponse(product);
    }

    @Transactional
    @CacheEvict(value = {CacheNames.CATALOG_PAGE, CacheNames.POPULAR_PRODUCTS, CacheNames.PRODUCT_DETAILS}, allEntries = true)
    public ProductResponse createProduct(ProductCreateRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateResourceException("SKU already exists");
        }

        Product product = new Product();
        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setActive(true);

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setAvailableQuantity(request.availableQuantity());
        product.setInventory(inventory);

        Product savedProduct = productRepository.save(product);
        return toResponse(savedProduct);
    }

    ProductResponse toResponse(Product product) {
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
