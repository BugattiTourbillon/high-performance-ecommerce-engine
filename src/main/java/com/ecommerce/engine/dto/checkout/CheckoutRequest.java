package com.ecommerce.engine.dto.checkout;

import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(
    @NotBlank String paymentToken
) {
}
