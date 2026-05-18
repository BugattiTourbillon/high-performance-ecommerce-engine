package com.ecommerce.engine.dto.product;

import jakarta.validation.constraints.Min;

public record InventoryAdjustmentRequest(
    @Min(0) int availableQuantity
) {
}
