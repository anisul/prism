package com.grasshopper.prism.logging;

import com.grasshopper.prism.domain.FilterEntry;
import com.grasshopper.prism.domain.SearchIntent;

import java.util.List;

public record SearchRequestLog(
        String requestId,
        String rawQuery,
        String extractedSearchText,
        List<FilterEntry> extractedFilters,
        double confidence,
        boolean transformed,
        long llmLatencyMs
) {
    public static SearchRequestLog of(
            String requestId,
            String rawQuery,
            SearchIntent intent,
            boolean transformed,
            long llmLatencyMs
    ) {
        return new SearchRequestLog(
                requestId,
                rawQuery,
                intent.searchText(),
                intent.filters(),
                intent.confidence(),
                transformed,
                llmLatencyMs
        );
    }

    @Override
    public String toString() {
        return String.format(
                "[SEARCH] requestId=%s | raw=\"%s\" | searchText=\"%s\" | filters=%s | confidence=%.2f | mode=%s | llmMs=%d",
                requestId,
                rawQuery,
                extractedSearchText,
                extractedFilters,
                confidence,
                transformed ? "TRANSFORMED" : "PASSTHROUGH",
                llmLatencyMs
        );
    }
}