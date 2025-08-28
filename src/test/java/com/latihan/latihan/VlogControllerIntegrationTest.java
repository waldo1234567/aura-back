package com.latihan.latihan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.latihan.latihan.Config.WebClientConfig;
import com.latihan.latihan.Service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.security.oauth2.client.provider.supabase.issuer-uri=disabled",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
        },
        classes = { LatihanApplication.class })
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@ImportAutoConfiguration(exclude = { WebClientConfig.class })
@TestPropertySource(properties = {
        "google.ai.api-key=dummy-test-key",
        "google.ai.endpoint=http://fake-endpoint"
})
public class VlogControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @MockBean
    private AnalyticsService analyticsService;

    @MockBean
    private WebClient webClient;
    @MockBean
    private com.latihan.latihan.Repository.SessionRepository sessionRepository;

    @BeforeEach
    void setup() {
        // mock service
        when(analyticsService.computeSpiderScores(
                anyMap(), anyMap(), anyMap(), anyMap(), anyMap(), anyMap()
        )).thenReturn(Map.of(
                "Stress", 10,
                "LowMood", 20,
                "SocialWithdrawal", 5,
                "Irritability", 5,
                "CognitiveFatigue", 10,
                "Arousal", 15,
                "confidence", 80
        ));

        // mock WebClient chain
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenAnswer(inv -> headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

// Build fake response
        Map<String, Object> fakeCandidateContent = Map.of(
                "parts", List.of(Map.of("text",
                        "OBSERVATIONS — Transcript:\n - NONE\n\nSTRUCTURED_SECTION_END\nSPIDER_DATA:10,20,5,5,10,15,CONF:80"
                ))
        );
        Map<String, Object> fakeCandidate = Map.of("content", fakeCandidateContent);
        Map<String, Object> aiResponse = Map.of("candidates", List.of(fakeCandidate));

        when(responseSpec.bodyToMono(eq(Map.class))).thenReturn(Mono.just(aiResponse));
    }

    @Test
    void postVlogs_returnsOkAndReply() throws Exception {
        // craft a minimal JSON request body (timeline entries)
        String json = """
                {
                  "transcript": "I had a tough week",
                  "usePolished": false,
                  "timeline": [
                    {
                      "time": 1000,
                      "hr": null,
                      "voice": {"time":1000, "valid": true, "pitch": 220.0, "volume": 0.2},
                      "expression": {"time":1000, "emotion":"neutral", "confidence":0.9}
                    },
                    {
                      "time": 3000,
                      "hr": {"time":2000, "bpm":72},
                      "voice": {"time":3000, "valid": true, "pitch": 210.0, "volume": 0.25},
                      "expression": {"time":3000, "emotion":"sad", "confidence":0.8}
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/vlogs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").exists())
                .andExpect(jsonPath("$.faceMetrics").exists())
                .andExpect(jsonPath("$.hrvMetrics").exists())
                .andExpect(jsonPath("$.riskSummary").exists());
    }
}
