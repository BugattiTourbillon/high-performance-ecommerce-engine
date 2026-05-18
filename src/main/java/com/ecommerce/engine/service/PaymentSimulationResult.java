package com.ecommerce.engine.service;

import com.ecommerce.engine.entity.PaymentStatus;

public record PaymentSimulationResult(
    PaymentStatus status,
    String providerReference
) {
}
