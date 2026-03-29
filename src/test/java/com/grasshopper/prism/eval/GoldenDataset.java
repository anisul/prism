package com.grasshopper.prism.eval;

import java.util.List;

public record GoldenDataset(List<GoldenCase> cases) {

    public record GoldenCase(
            String input,
            String expectedSearchText,
            List<ExpectedFilter> expectedFilters,
            String notes
    ) {}

    public record ExpectedFilter(String key, String value) {}
}