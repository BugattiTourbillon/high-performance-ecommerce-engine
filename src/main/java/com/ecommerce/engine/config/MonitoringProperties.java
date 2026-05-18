package com.ecommerce.engine.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.monitoring")
public class MonitoringProperties {

    @Min(1)
    private long slowCallThresholdMs;
}
