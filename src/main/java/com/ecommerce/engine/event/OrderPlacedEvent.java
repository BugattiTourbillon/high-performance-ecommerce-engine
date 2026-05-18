package com.ecommerce.engine.event;

public record OrderPlacedEvent(
    Long orderId,
    String customerEmail
) {
}
