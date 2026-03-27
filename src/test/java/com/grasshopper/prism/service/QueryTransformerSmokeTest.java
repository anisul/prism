package com.grasshopper.prism.service;

import com.grasshopper.prism.domain.SearchIntent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class QueryTransformerSmokeTest {

    @Autowired
    QueryTransformerService transformer;

    @Test
    void smokeTest() {
        String[] queries = {
                "cheap red Nike shoes",
                "large blue sofa",
                "laptop",
                "good stuff"
        };

        for (String query : queries) {
            SearchIntent intent = transformer.transform(query);
            System.out.printf("""
                    ─────────────────────────────────
                    Query     : %s
                    SearchText: %s
                    Filters   : %s
                    Confidence: %.2f
                    %n""",
                    query,
                    intent.searchText(),
                    intent.filters(),
                    intent.confidence()
            );

        }
    }
}