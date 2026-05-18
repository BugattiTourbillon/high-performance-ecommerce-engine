package com.ecommerce.engine.dto.order;

import java.math.BigDecimal;

public record OrderItemResponse(
    Long productId,
    String productName,
    String sku,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {
}
