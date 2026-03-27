package com.grasshopper.prism.domain;

import java.util.List;

public record SearchApiRequest(
        String searchText,
        List<FilterEntry> filters,
        boolean transformed
) {
    public static SearchApiRequest from(SearchIntent intent, boolean transformed) {
        return new SearchApiRequest(intent.searchText(), intent.filters(), transformed);
    }

    public static SearchApiRequest passthrough(String rawQuery) {
        return new SearchApiRequest(rawQuery, List.of(), false);
    }

    /** Builds the query param string sent to upstream e.g. q=shoes&color=red&brand=nike */
    public String toQueryString() {
        StringBuilder sb = new StringBuilder();

        if (searchText != null && !searchText.isBlank()) {
            sb.append("q=").append(searchText.replace(" ", "+"));
        }

        filters.forEach(f ->
                sb.append(sb.isEmpty() ? "" : "&")
                        .append(f.key()).append("=").append(f.value())
        );

        return sb.toString();
    }
}
