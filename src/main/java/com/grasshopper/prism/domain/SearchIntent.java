package com.grasshopper.prism.domain;

import java.util.List;

public record SearchIntent(
        String searchText,
        List<FilterEntry> filters,
        double confidence
) {
    public static SearchIntent passthrough(String rawQuery) {
        return new SearchIntent(rawQuery, List.of(), 0.0);
    }
}