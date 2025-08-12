package com.latihan.latihan.Controller;

import com.latihan.latihan.Config.WebClientConfig;
import com.latihan.latihan.DTO.*;
import com.latihan.latihan.Service.AnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class VlogController {
    @Autowired
    private final AnalyticsService analyticsService;
    @Autowired
    private final WebClient webClient;

    @PostMapping("/vlogs")
    public ResponseEntity<?> handleVlog(@Valid @RequestBody SessionData data){
        List<ExpressionPoint> expressions = data.getTimeline().stream()
                .map(TimelineEntry::getExpression)
                .filter(Objects::nonNull)
                .toList();

        List<HrPoint> heartRates = data.getTimeline().stream()
                .map(TimelineEntry::getHr)
                .filter(Objects::nonNull)
                .toList();

        List<VoicePoint> voicePoints = data.getTimeline().stream()
                .map(TimelineEntry::getVoice)
                .filter(Objects::nonNull)
                .toList();

        Map<String, Object> faceMetrics = analyticsService.summarizeFace(expressions);
        Map<String, Double> hrvMetrics = analyticsService.computeHrv(heartRates);
        Map<String, Double> voiceMetrics = analyticsService.summarizeVoice(voicePoints);

        Map<String, Double> exprAdv = analyticsService.summarizeFaceAdv(expressions);
        Map<String, Object> voiceAdv = analyticsService.summarizeVoiceAdv(voicePoints);
        Map<String, Double> hrvFreq = analyticsService.computeFrequencyHrv(heartRates);

        String prompt = buildInterpretationPrompt(
                data.getTranscript(),
                faceMetrics, exprAdv,
                hrvMetrics, hrvFreq,
                voiceMetrics, voiceAdv
        );
        Map<String, Object> part = Map.of("text", prompt);

        // 2. Create the 'contents' object with the 'role' and 'parts'
        Map<String, Object> content = Map.of(
                "role", "user",
                "parts", List.of(part)
        );

        // 3. Create the 'generationConfig' object for the parameters
        Map<String, Object> generationConfig = Map.of(
                "temperature", 0.3,
                "maxOutputTokens", 1024,
                "topP", 0.95,
                "topK", 40
        );

        // 4. Build the final request body with "contents" and "generationConfig"
        Map<String,Object> body = Map.of(
                "contents", List.of(content),
                "generationConfig", generationConfig
        );
        // --- END OF CORRECTION ---

        System.out.println("Request Body: " + body);

        Map<?,?> aiResponse = webClient.post()
                .uri("/v1beta/models/gemini-2.0-flash:generateContent")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<?> messages = (List<?>)aiResponse.get("messages");
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> candidates =
                (List<Map<String,Object>>) aiResponse.get("candidates");

        if (candidates != null && !candidates.isEmpty()) {
            Map<String, Object> firstCandidate = candidates.get(0);

            // The output is inside a "content" object, which contains a "parts" array
            Map<String, Object> responseContent = (Map<String, Object>) firstCandidate.get("content");

            // Check if responseContent is not null before accessing "parts"
            String aiReply = "";
            if (responseContent != null) {
                List<Map<String, Object>> parts = (List<Map<String, Object>>) responseContent.get("parts");

                // Check if parts is not null or empty
                if (parts != null && !parts.isEmpty()) {
                    // The actual text is under the key "text"
                    aiReply = (String) parts.get(0).get("text");
                }
            }

            System.out.println(aiReply);
            return ResponseEntity.ok(Map.of(
                    "reply", aiReply,
                    "faceMetrics", faceMetrics,
                    "hrvMetrics", hrvMetrics,
                    "voiceMetrics", voiceMetrics
            ));
        } else {
            // Handle the case where the API returns no candidates, possibly due to a safety filter
            System.out.println("AI response did not contain any candidates. Check safety settings or prompt.");
            return ResponseEntity.status(400).body(Map.of("error", "AI response was blocked or malformed."));
        }
    }


    private String buildInterpretationPrompt(
            String transcript,
            // original face
            Map<String, Object> face,
            // advanced facial features
            Map<String, Double> faceAdv,
            // time-domain HRV
            Map<String, Double> hrvTime,
            // frequency-domain HRV
            Map<String, Double> hrvFreq,
            // original voice summary
            Map<String, Double> voiceTime,
            // advanced voice summary
            Map<String, Object> voiceAdv
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an empathetic coach. Here are the session metrics:\n\n");

        // --- Face ---
        sb.append("Facial Expressions:\n");
        sb.append(" • Percent time: ").append(face.get("percentTime")).append("\n");
        sb.append(" • Avg confidence: ").append(face.get("avgConfidence")).append("\n");
        sb.append(" • Avg EAR: ").append(String.format("%.3f", faceAdv.get("avgEar"))).append("\n");
        sb.append(" • Blink rate: ").append(String.format("%.2f blinks/sec", faceAdv.get("avgBlinkRate"))).append("\n\n");

        // --- HRV ---
        sb.append("Heart Rate Variability (time-domain):\n");
        sb.append(" • SDNN = ").append(String.format("%.1f ms", hrvTime.get("SDNN"))).append("\n");
        sb.append(" • RMSSD= ").append(String.format("%.1f ms", hrvTime.get("RMSSD"))).append("\n");
        sb.append(" • pNN50= ").append(String.format("%.1f%%", hrvTime.get("pNN50"))).append("\n\n");

        sb.append("Heart Rate Variability (frequency-domain):\n");
        sb.append(" • LF power = ").append(String.format("%.1f", hrvFreq.get("LF"))).append("\n");
        sb.append(" • HF power = ").append(String.format("%.1f", hrvFreq.get("HF"))).append("\n");
        sb.append(" • LF/HF ratio = ").append(String.format("%.2f", hrvFreq.get("LF/HF"))).append("\n\n");

        // --- Voice ---
        sb.append("Voice Summary (time-domain):\n");
        sb.append(" • Avg volume = ").append(String.format("%.3f", voiceTime.get("avgVolume"))).append("\n");
        sb.append(" • Max volume = ").append(String.format("%.3f", voiceTime.get("maxVolume"))).append("\n");
        sb.append(" • Avg pitch = ").append(String.format("%.1f Hz", voiceTime.get("avgPitch"))).append("\n\n");

        sb.append("Voice Spectral Features:\n");
        @SuppressWarnings("unchecked")
        List<Double> avgMfcc = (List<Double>) voiceAdv.get("avgMfcc");
        sb.append(" • Avg MFCCs = ").append(avgMfcc).append("\n");
        sb.append(" • Avg spectral centroid = ")
                .append(String.format("%.3f", voiceAdv.get("avgSpectralCentroid"))).append("\n");
        sb.append(" • Avg ZCR = ")
                .append(String.format("%.3f", voiceAdv.get("avgZcr"))).append("\n\n");

        // --- Transcript & instructions ---
        sb.append("Transcript: \"").append(transcript).append("\"\n\n");
        sb.append("Please offer 3–5 observations about their emotional state based on these metrics, and one actionable tip.\n");

        return sb.toString();
    }


}
