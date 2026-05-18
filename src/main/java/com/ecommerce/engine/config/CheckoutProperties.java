package com.ecommerce.engine.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.checkout")
public class CheckoutProperties {

    @Min(1)
    private int maxConcurrentPaymentSimulations;
}
