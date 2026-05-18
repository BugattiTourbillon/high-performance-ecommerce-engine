package com.ecommerce.engine.dto.product;

import java.math.BigDecimal;

public record ProductResponse(
    Long id,
    String sku,
    String name,
    String description,
    BigDecimal price,
    boolean active,
    int availableQuantity,
    long popularityScore,
    Long version
) {
}
