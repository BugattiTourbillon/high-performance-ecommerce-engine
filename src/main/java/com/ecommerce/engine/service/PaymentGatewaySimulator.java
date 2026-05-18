package com.ecommerce.engine.service;

import com.ecommerce.engine.config.CheckoutProperties;
import com.ecommerce.engine.exception.PaymentFailedException;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewaySimulator {

    private final CheckoutProperties checkoutProperties;

    private Semaphore concurrencyGuard;

    @PostConstruct
    void initialize() {
        this.concurrencyGuard = new Semaphore(checkoutProperties.getMaxConcurrentPaymentSimulations(), true);
    }

    public PaymentSimulationResult authorize(String paymentToken, BigDecimal amount) {
        boolean permitAcquired = false;
        try {
            // This semaphore protects the simulated downstream payment provider from a local
            // thundering herd. When the provider is saturated we fail fast instead of letting
            // request threads pile up indefinitely and exhaust the application under load.
            permitAcquired = concurrencyGuard.tryAcquire(250, TimeUnit.MILLISECONDS);
            if (!permitAcquired) {
                throw new PaymentFailedException("Payment gateway is saturated; please retry");
            }

            simulateNetworkLatency();

            String normalizedToken = paymentToken.toLowerCase(Locale.ROOT);
            if (!normalizedToken.startsWith("tok_") || normalizedToken.contains("decline")) {
                throw new PaymentFailedException("Payment was declined by the simulator");
            }
            if (amount.signum() <= 0) {
                throw new PaymentFailedException("Payment amount must be positive");
            }

            return new PaymentSimulationResult(
                com.ecommerce.engine.entity.PaymentStatus.AUTHORIZED,
                "PAY-" + UUID.randomUUID()
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PaymentFailedException("Payment was interrupted");
        } finally {
            if (permitAcquired) {
                concurrencyGuard.release();
            }
        }
    }

    private void simulateNetworkLatency() {
        try {
            Thread.sleep(Duration.ofMillis(75));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while simulating payment network latency", ex);
            throw new PaymentFailedException("Payment processing was interrupted");
        }
    }
}
