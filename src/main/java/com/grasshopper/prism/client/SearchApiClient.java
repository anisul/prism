package com.grasshopper.prism.client;

import com.grasshopper.prism.config.SearchProperties;
import com.grasshopper.prism.domain.SearchApiRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class SearchApiClient {

    private static final Logger log = LoggerFactory.getLogger(SearchApiClient.class);

    private final WebClient webClient;

    public SearchApiClient(SearchProperties properties, WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl(properties.api().baseUrl())
                .build();
    }

    public String search(SearchApiRequest request) {
        String queryString = request.toQueryString();
        log.info("Upstream request: {}?{}", "/search", queryString);

        return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/search");

                    if (request.searchText() != null && !request.searchText().isBlank()) {
                        builder.queryParam("q", request.searchText());
                    }

                    request.filters().forEach(f ->
                            builder.queryParam(f.key(), f.value())
                    );

                    return builder.build();
                })
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
