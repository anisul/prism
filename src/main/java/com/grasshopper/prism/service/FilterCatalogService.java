package com.grasshopper.prism.service;

import java.util.Map;
import java.util.Set;

public interface FilterCatalogService {

    /** All valid keys */
    Set<String> keys();

    /** All valid values for a given key, empty if key unknown */
    Set<String> valuesFor(String key);

    /** Full catalog as key → allowed values */
    Map<String, Set<String>> catalog();

    /** True if this key+value pair is in the allowlist */
    default boolean isValid(String key, String value) {
        return valuesFor(key).contains(value.toLowerCase());
    }
}