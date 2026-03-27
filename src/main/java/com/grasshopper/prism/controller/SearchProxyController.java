package com.grasshopper.prism.controller;

import com.grasshopper.prism.client.SearchApiClient;
import com.grasshopper.prism.config.SearchProperties;
import com.grasshopper.prism.domain.SearchApiRequest;
import com.grasshopper.prism.domain.SearchIntent;
import com.grasshopper.prism.domain.TransformResult;
import com.grasshopper.prism.logging.SearchRequestLog;
import com.grasshopper.prism.service.QueryTransformerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
        String requestId = MDC.get("requestId");
        log.info("Incoming query: '{}' | requestId={}", q, requestId);

        TransformResult result = transformer.transform(q);
        SearchIntent intent = result.intent();
        boolean transformed = intent.confidence() >= properties.confidenceThreshold();

        SearchRequestLog searchLog = SearchRequestLog.of(
                requestId, q, intent, transformed, result.llmLatencyMs()
        );
        log.info(searchLog.toString());

        SearchApiRequest request = transformed
                ? SearchApiRequest.from(intent, true)
                : SearchApiRequest.passthrough(q);

        String upstreamResponse = searchApiClient.search(request);

        return ResponseEntity.ok()
                .header("X-Request-Id", requestId)
                .header("X-Search-Transformed", String.valueOf(transformed))
                .header("X-Search-Confidence", String.valueOf(intent.confidence()))
                .header("X-Search-Filters", intent.filters().toString())
                .header("X-Search-Latency-Ms", String.valueOf(result.llmLatencyMs()))
                .body(upstreamResponse);
    }
}