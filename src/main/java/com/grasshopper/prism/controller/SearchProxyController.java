package com.grasshopper.prism.controller;

import com.grasshopper.prism.client.SearchApiClient;
import com.grasshopper.prism.config.SearchProperties;
import com.grasshopper.prism.domain.SearchApiRequest;
import com.grasshopper.prism.domain.SearchIntent;
import com.grasshopper.prism.service.QueryTransformerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchProxyController {

    private static final Logger log = LoggerFactory.getLogger(SearchProxyController.class);

    private final QueryTransformerService transformer;
    private final SearchApiClient searchApiClient;
    private final SearchProperties properties;

    public SearchProxyController(
            QueryTransformerService transformer,
            SearchApiClient searchApiClient,
            SearchProperties properties
    ) {
        this.transformer = transformer;
        this.searchApiClient = searchApiClient;
        this.properties = properties;
    }

    @GetMapping
    public ResponseEntity<String> search(@RequestParam String q) {
        log.info("Incoming query: '{}'", q);

        SearchIntent intent = transformer.transform(q);
        boolean transformed = intent.confidence() >= properties.confidenceThreshold();

        SearchApiRequest request = transformed
                ? SearchApiRequest.from(intent, true)
                : SearchApiRequest.passthrough(q);

        log.info("Mode: {} | confidence: {} | queryString: {}",
                transformed ? "TRANSFORMED" : "PASSTHROUGH",
                intent.confidence(),
                request.toQueryString());

        String upstreamResponse = searchApiClient.search(request);

        return ResponseEntity.ok()
                .header("X-Search-Transformed", String.valueOf(transformed))
                .header("X-Search-Confidence", String.valueOf(intent.confidence()))
                .header("X-Search-Filters", intent.filters().toString())
                .body(upstreamResponse);
    }
}