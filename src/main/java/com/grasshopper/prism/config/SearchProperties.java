package com.grasshopper.prism.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search")
public record SearchProperties(
        double confidenceThreshold,
        ApiProperties api
) {
    public record ApiProperties(String baseUrl) {}
}
