package com.latihan.latihan.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
public class WebClientConfig {
    @Value("${google.ai.api-key}")
    private String apiKey;

    @Value("${google.ai.endpoint}")
    private String aiEndpoint;


    @Bean
    public WebClient webClient(WebClient.Builder builder){
        System.out.println("DEBUG: Value of aiEndpoint = " + aiEndpoint); // <--- ADD THIS LINE
        System.out.println("DEBUG: Value of apiKey = " + apiKey);

        if (aiEndpoint == null || aiEndpoint.isEmpty()) {
            throw new IllegalArgumentException("aiEndpoint property is not configured or is empty!");
        }

        return builder
                .baseUrl("https://generativelanguage.googleapis.com")
                .defaultHeader("X-Goog-Api-Key", apiKey)
                .build();
    }
}
