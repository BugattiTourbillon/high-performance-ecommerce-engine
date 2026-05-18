package com.ecommerce.engine.dto.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    Long orderId,
    String status,
    BigDecimal totalAmount,
    int totalItems,
    String paymentReference,
    Instant createdAt,
    List<OrderItemResponse> items
) {
}
