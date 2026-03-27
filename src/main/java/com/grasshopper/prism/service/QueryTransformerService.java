package com.grasshopper.prism.service;

import com.grasshopper.prism.domain.FilterEntry;
import com.grasshopper.prism.domain.SearchIntent;
import com.grasshopper.prism.domain.TransformResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QueryTransformerService {

    private static final Logger log = LoggerFactory.getLogger(QueryTransformerService.class);

    private final ChatClient chatClient;
    private final FilterCatalogService catalogService;
    private final BeanOutputConverter<SearchIntent> outputConverter;
    private final String systemPrompt;

    public QueryTransformerService(
            ChatClient.Builder builder,
            FilterCatalogService catalogService
    ) {
        this.catalogService = catalogService;
        this.outputConverter = new BeanOutputConverter<>(SearchIntent.class);
        this.chatClient = builder.build();
        this.systemPrompt = buildSystemPrompt();
    }

    public TransformResult transform(String rawQuery) {
        try {
            long start = System.currentTimeMillis();

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(u -> u.text("""
                        Extract search intent from this query: {query}
                        
                        {format}
                        """)
                            .param("query", rawQuery)
                            .param("format", outputConverter.getFormat()))
                    .call()
                    .content();

            long latencyMs = System.currentTimeMillis() - start;
            SearchIntent intent = outputConverter.convert(response);
            return new TransformResult(validate(intent, rawQuery), latencyMs);

        } catch (Exception e) {
            log.warn("LLM call failed for query '{}', falling back. Reason: {}", rawQuery, e.getMessage());
            return new TransformResult(SearchIntent.passthrough(rawQuery), 0L);
        }
    }

    // Strip any filters the LLM invented that aren't in the catalog
    private SearchIntent validate(SearchIntent intent, String rawQuery) {
        if (intent == null) {
            log.warn("LLM returned null intent for query '{}', falling back", rawQuery);
            return SearchIntent.passthrough(rawQuery);
        }

        List<FilterEntry> validFilters = intent.filters().stream()
                .filter(f -> catalogService.isValid(f.key(), f.value()))
                .toList();

        int dropped = intent.filters().size() - validFilters.size();
        if (dropped > 0) {
            log.debug("Dropped {} hallucinated filter(s) for query '{}'", dropped, rawQuery);
        }

        return new SearchIntent(intent.searchText(), validFilters, intent.confidence());
    }

    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();

        sb.append("""
                You are a search intent extractor for an e-commerce platform.
                
                Your job is to analyse a natural language search query and extract:
                1. A clean searchText — the core noun or product term, stripped of filter words.
                   If the entire query is covered by filters, return an empty string for searchText.
                2. A list of filters that match EXACTLY the allowed catalog below.
                3. A confidence score from 0.0 to 1.0 reflecting how clearly the query maps to known filters.
                
                Rules:
                - Only extract filters that are explicitly stated or very strongly implied.
                - If you are not confident a filter applies, omit it rather than guessing.
                - Extract ALL applicable filters, not just the most obvious one.
                - Normalise synonyms to their exact catalog value:
                            "cheap" / "affordable" / "inexpensive" → price_range:budget
                            "expensive" / "high-end" → price_range:premium
                            "luxury" / "designer" → price_range:luxury
                            "mid range" / "moderate" → price_range:mid
                            "large" / "big" / "extra large" → size:L
                            "small" / "tiny" / "compact" → size:S
                            "medium" → size:M
                - Never invent filter keys or values that are not in the catalog.
                - Confidence should be low (< 0.3) when the query is vague or has no extractable filters.
                
                Allowed filter catalog:
                """);

        catalogService.catalog().forEach((key, values) ->
                sb.append(String.format("  %s: %s%n", key, String.join(", ", values)))
        );

        sb.append("""
            
            Examples:
            Input: "cheap red Nike shoes"
            Output: searchText="shoes", filters=[color:red, brand:nike, category:footwear, price_range:budget], confidence=0.95
            
            Input: "large blue sofa"
            Output: searchText="sofa", filters=[color:blue, size:L, category:furniture], confidence=0.90
            
            Input: "good stuff"
            Output: searchText="", filters=[], confidence=0.05
            
            Input: "laptop"
            Output: searchText="laptop", filters=[], confidence=0.10
            """);

        return sb.toString();
    }
}
