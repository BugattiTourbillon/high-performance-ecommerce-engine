package com.ecommerce.engine.dto.cart;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CartResponse(
    Long cartId,
    List<CartItemResponse> items,
    BigDecimal totalAmount,
    Instant updatedAt
) {
}
