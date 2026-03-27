package com.grasshopper.prism.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record SearchIntent(

        @JsonProperty(required = true)
        @JsonPropertyDescription("The clean search text stripped of all filter words")
        String searchText,

        @JsonProperty(required = true)
        @JsonPropertyDescription("List of extracted filters matching the catalog")
        List<FilterEntry> filters,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Confidence score between 0.0 and 1.0")
        double confidence

) {
    public static SearchIntent passthrough(String rawQuery) {
        return new SearchIntent(rawQuery, List.of(), 0.0);
    }
}