package com.grasshopper.prism.service;

import com.grasshopper.prism.domain.SearchIntent;
import com.grasshopper.prism.domain.TransformResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;


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
            var start = System.currentTimeMillis();

            var response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(u -> u.text("""
                        Extract search intent from this query: {query}
                        
                        {format}
                        """)
                            .param("query", rawQuery)
                            .param("format", outputConverter.getFormat()))
                    .call()
                    .content();

            var latencyMs = System.currentTimeMillis() - start;

            assert response != null;
            var intent = outputConverter.convert(response);

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

        var validFilters = intent.filters().stream()
                .filter(f -> catalogService.isValid(f.key(), f.value()))
                .toList();

        int dropped = intent.filters().size() - validFilters.size();
        if (dropped > 0) {
            log.debug("Dropped {} hallucinated filter(s) for query '{}'", dropped, rawQuery);
        }

        return new SearchIntent(intent.searchText(), validFilters, intent.confidence());
    }

    private String buildSystemPrompt() {
        var sb = new StringBuilder();

        sb.append("""
            You are a search intent extractor for an e-commerce platform.
            
            Your job is to analyse a natural language search query and extract:
            1. A clean searchText — the core product noun only.
               - Strip ALL filter words (colors, brands, sizes, price words).
               - Strip ALL filler words: "show me", "I want", "something", "products",
                 "items", "things", "me", "a", "the".
               - If the category itself is the only noun and it maps to a catalog filter,
                 return empty string — do not repeat the category as searchText.
               - If a specific product type exists beyond the category
                 (e.g. "t-shirt", "sofa", "headphones"), keep it as searchText.
            2. A list of filters matching EXACTLY the allowed catalog below.
            3. A confidence score 0.0–1.0.
            
            Rules:
            - Only extract filters explicitly stated or very strongly implied.
            - If not confident a filter applies, omit it rather than guessing.
            - Extract ALL applicable filters, not just the most obvious one.
            - Normalise synonyms to exact catalog values:
                "cheap" / "affordable" / "inexpensive" → price_range:budget
                "expensive" / "high-end"               → price_range:premium
                "luxury" / "designer"                  → price_range:luxury
                "mid range" / "moderate"               → price_range:mid
                size (clothing and footwear only):
                "large" / "big"                        → size:L
                "small" / "tiny"                       → size:S
                "medium"                               → size:M
            - Never invent filter keys or values not in the catalog.
            - Confidence < 0.3 when query is vague with no extractable filters.
            - If the query is purely vague with no product signal, searchText = "".
            
            Allowed filter catalog:
            """);

        catalogService.catalog().forEach((key, values) ->
                sb.append(String.format("  %s: %s%n", key, String.join(", ", values)))
        );

        sb.append("""
            Examples:
            Input:  "cheap red Nike shoes"
            Output: searchText="shoes", filters=[color:red, brand:nike, category:footwear, price_range:budget], confidence=0.95
            
            Input:  "Nike products"
            Output: searchText="", filters=[brand:nike], confidence=0.85
            
            Input:  "show me furniture"
            Output: searchText="", filters=[category:furniture], confidence=0.90
            
            Input:  "something blue"
            Output: searchText="", filters=[color:blue], confidence=0.50
            
            Input:  "premium Apple electronics"
            Output: searchText="", filters=[brand:apple, category:electronics, price_range:premium], confidence=0.95
            
            Input:  "large blue sofa"
            Output: searchText="sofa", filters=[color:blue, category:furniture], confidence=0.90
            
            Input:  "good stuff"
            Output: searchText="", filters=[], confidence=0.05
            
            Input:  "laptop"
            Output: searchText="laptop", filters=[], confidence=0.10
            """);

        return sb.toString();
    }
}
