package com.latihan.latihan;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@TestConfiguration
public class WebClientTestConfig {
    @Bean
    public WebClient webClient() {
        return Mockito.mock(WebClient.class);
    }
}
