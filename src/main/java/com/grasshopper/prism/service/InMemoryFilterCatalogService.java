package com.grasshopper.prism.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Service
public class InMemoryFilterCatalogService implements FilterCatalogService {

    private final Map<String, Set<String>> catalog;

    public InMemoryFilterCatalogService() {
        catalog = Map.of(
                "category", Set.of(
                        "electronics", "clothing", "footwear",
                        "furniture", "books", "sports", "beauty", "toys"
                ),
                "brand", Set.of(
                        "nike", "apple", "samsung", "sony",
                        "ikea", "adidas", "zara", "lego"
                ),
                "color", Set.of(
                        "red", "blue", "green", "black",
                        "white", "yellow", "pink", "grey"
                ),
                "size", Set.of("XS", "S", "M", "L", "XL", "XXL"),
                "price_range", Set.of("budget", "mid", "premium", "luxury")
        );
    }

    @Override
    public Set<String> keys() {
        return catalog.keySet();
    }

    @Override
    public Set<String> valuesFor(String key) {
        return catalog.getOrDefault(key.toLowerCase(), Set.of());
    }

    @Override
    public Map<String, Set<String>> catalog() {
        return Collections.unmodifiableMap(catalog);
    }
}
