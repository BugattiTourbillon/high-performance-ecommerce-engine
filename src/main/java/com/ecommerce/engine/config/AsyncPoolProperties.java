package com.ecommerce.engine.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.async")
public class AsyncPoolProperties {

    @Min(1)
    private int corePoolSize;

    @Min(1)
    private int maxPoolSize;

    @Min(1)
    private int queueCapacity;

    @NotBlank
    private String threadNamePrefix;
}
