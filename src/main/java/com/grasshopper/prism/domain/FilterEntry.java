package com.grasshopper.prism.domain;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record FilterEntry(

        @JsonPropertyDescription("Filter key, e.g. color, brand, size")
        String key,

        @JsonPropertyDescription("Filter value from the allowed catalog")
        String value
) {}
