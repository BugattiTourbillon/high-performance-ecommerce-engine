package com.ecommerce.engine.dto.cart;

import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(
    @Min(0) int quantity
) {
}
