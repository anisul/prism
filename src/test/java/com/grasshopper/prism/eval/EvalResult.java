package com.grasshopper.prism.eval;

import com.grasshopper.prism.domain.FilterEntry;

import java.util.List;

public record EvalResult(
        String input,
        String expectedSearchText,
        String actualSearchText,
        List<GoldenDataset.ExpectedFilter> expectedFilters,
        List<FilterEntry> actualFilters,
        List<GoldenDataset.ExpectedFilter> correctFilters,
        List<GoldenDataset.ExpectedFilter> missedFilters,
        List<FilterEntry> hallucinatedFilters,
        boolean searchTextMatch,
        double precision,
        double recall,
        double confidence,
        String notes
) {
    public boolean perfect() {
        return missedFilters.isEmpty() && hallucinatedFilters.isEmpty() && searchTextMatch;
    }
}