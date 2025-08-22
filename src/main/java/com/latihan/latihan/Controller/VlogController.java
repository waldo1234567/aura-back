package com.latihan.latihan.Controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.latihan.latihan.DTO.*;
import com.latihan.latihan.Entity.SessionEntity;
import com.latihan.latihan.Repository.SessionRepository;
import com.latihan.latihan.Service.AnalyticsService;
import com.latihan.latihan.Service.SpiderDataValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class VlogController {
    @Autowired
    private final AnalyticsService analyticsService;
    @Autowired
    private final WebClient webClient;
    @Autowired
    private final SessionRepository sessionRepository;

    @PostMapping("/vlogs")
    public ResponseEntity<?> handleVlog(@Valid @RequestBody SessionData data) throws JsonProcessingException {
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
        analyticsService.diagnoseAndComputeHrMetrics(heartRates);
        Map<String,Object> riskSummary = analyticsService.computeRiskSummary(faceMetrics, hrvMetrics, voiceMetrics, data.getTranscript());
        Map<String,Object> diagnostics = analyticsService.computeDiagnosticsFromTimeline(data.getTimeline(), data.getTranscript());

        Map<String,Object> metrics = new HashMap<>();
        metrics.put("face", faceMetrics);
        metrics.put("hrvTime", hrvMetrics);
        metrics.put("hrvFreq", hrvFreq);
        metrics.put("voiceTime", voiceMetrics);
        metrics.put("voiceAdv", voiceAdv);
        metrics.put("diagnostics", diagnostics);

        System.out.print("tes");
        List<String> dangerKeywords = List.of("suicide","kill myself","i can't go on","don't want to live","hopeless","end my life","suicidal");


        String detectedLang = analyticsService.detectLanguage(data.getTranscript());
        String translated = analyticsService.translateToEnglish(data.getTranscript());

        ObjectMapper mapper = new ObjectMapper();
        String faceMetricsJson = mapper.writeValueAsString(faceMetrics);
        String hrvMetricsJson = mapper.writeValueAsString(hrvMetrics);
        String voiceMetricsJson = mapper.writeValueAsString(voiceMetrics);
        String riskSummaryJson = mapper.writeValueAsString(riskSummary);

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

            Map<String, Object> responseContent = (Map<String, Object>) firstCandidate.get("content");


            String aiReply = "";
            if (responseContent != null) {
                List<Map<String, Object>> parts = (List<Map<String, Object>>) responseContent.get("parts");

                if (parts != null && !parts.isEmpty()) {
                    aiReply = (String) parts.get(0).get("text");
                }
            }
            System.out.println(aiReply);
            Map<String,Object> parsedSpider = SpiderDataValidator.parseAndValidateSpiderData(aiReply, metrics, analyticsService);
            Map<String,Object> parsed = analyticsService.parseAiReplyForStructuredData(aiReply, mapper);

            @SuppressWarnings("unchecked")
            List<Map<String,Object>> parsedFlagged = (List<Map<String,Object>>) parsed.get("flaggedExcerpts");
            @SuppressWarnings("unchecked")
            List<Map<String,Object>> parsedActions = (List<Map<String,Object>>) parsed.get("actionableHighlights");

            System.out.println("Parsed flagged excerpts from AI: " + parsedFlagged);
            System.out.println("Parsed action items from AI: " + parsedActions);

            String flaggedExcerptsJsonToSave = mapper.writeValueAsString(parsedFlagged);
            String actionItemsJsonToSave   = mapper.writeValueAsString(parsedActions);

            String spiderJson = mapper.writeValueAsString(parsedSpider);

            SessionEntity session = SessionEntity.builder()
                    .transcript(data.getTranscript())
                    .usePolished(data.getUsePolished())
                    .timeline(mapper.writeValueAsString(data.getTimeline()))
                    .faceMetrics(faceMetricsJson)
                    .hrvMetrics(hrvMetricsJson)
                    .voiceMetrics(voiceMetricsJson)
                    .riskSummary(riskSummaryJson)
                    .flaggedExcerpts(flaggedExcerptsJsonToSave)
                    .actionableHighlights(actionItemsJsonToSave)
                    .detectedLanguage(detectedLang)
                    .translatedTranscript(translated)
                    .versions("[{\"version\":1, \"transcript\":\"" + data.getTranscript().replace("\"","'") + "\", \"createdAt\":\"" + LocalDateTime.now().toString() + "\"}]")
                    .audioUrl(null)
                    .spiderData(spiderJson)// stub, or supply if available
                    .aiReply(aiReply) // you can keep the AI reply here if you have it
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();

            sessionRepository.save(session);

            Map<String,Object> response = new HashMap<>();
            response.put("reply", aiReply);
            response.put("faceMetrics", faceMetrics);
            response.put("hrvMetrics", hrvMetrics);
            response.put("voiceMetrics", voiceMetrics);
            response.put("riskSummary", riskSummary);
            response.put("flaggedExcerpts", flaggedExcerptsJsonToSave);
            response.put("actionableHighlights", actionItemsJsonToSave);
            response.put("detectedLanguage", detectedLang);
            response.put("translatedTranscript", translated);
            response.put("sessionId", session.getId());
            response.put("spiderJson", session.getSpiderData());
            response.put("privacyNote", "Demo only — metrics persisted, no raw media retained.");
            return ResponseEntity.ok(response);
        } else {
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

        sb.append("Facial Expressions:\n");
        sb.append(" • Percent time: ").append(face.get("percentTime")).append("\n");
        sb.append(" • Avg confidence: ").append(face.get("avgConfidence")).append("\n");
        sb.append(" • Avg EAR: ").append(String.format("%.3f", faceAdv.get("avgEar"))).append("\n");
        sb.append(" • Blink rate: ").append(String.format("%.2f blinks/sec", faceAdv.get("avgBlinkRate"))).append("\n\n");

        sb.append("Heart Rate Variability (time-domain):\n");
        sb.append(" • SDNN = ").append(String.format("%.1f ms", hrvTime.get("SDNN"))).append("\n");
        sb.append(" • RMSSD= ").append(String.format("%.1f ms", hrvTime.get("RMSSD"))).append("\n");
        sb.append(" • pNN50= ").append(String.format("%.1f%%", hrvTime.get("pNN50"))).append("\n\n");

        sb.append("Heart Rate Variability (frequency-domain):\n");
        sb.append(" • LF power = ").append(String.format("%.1f", hrvFreq.get("LF"))).append("\n");
        sb.append(" • HF power = ").append(String.format("%.1f", hrvFreq.get("HF"))).append("\n");
        sb.append(" • LF/HF ratio = ").append(String.format("%.2f", hrvFreq.get("LF/HF"))).append("\n\n");

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

        sb.append("Transcript: \"").append(transcript).append("\"\n\n");

        sb.append("TASK (strict, follow order & headers exactly):\n\n");

        sb.append("1) OBSERVATIONS — Transcript:\n");
        sb.append(" • Write 2–3 empathetic reflections that connect transcript phrases to underlying feelings or needs (not just restating). Go deeper into emotional meaning, context, or possible struggles. One or two sentences each.\n\n");

        sb.append("2) OBSERVATIONS — HRV (time-domain):\n");
        sb.append(" • Interpret the SDNN, RMSSD, and pNN50 values with compassion. Instead of only labeling them 'low' or 'high', explain what that might feel like for the person (e.g., tension, difficulty unwinding) and why it matters for their emotional wellbeing. 1–2 sentences per metric.\n\n");

        sb.append("3) OBSERVATIONS — HRV (frequency-domain):\n");
        sb.append(" • Explain what the LF, HF, and LF/HF ratio suggest about balance between stress and recovery systems. Use empathetic, plain language (e.g., 'Your body seems to be working harder to stay alert, which can feel exhausting.').\n\n");

        sb.append("4) OBSERVATIONS — Facial expressions:\n");
        sb.append(" • Go beyond percentages — describe what the dominant facial state could reflect emotionally (e.g., 'a neutral expression most of the time may signal holding emotions in'). Keep it gentle and interpretive.\n\n");

        sb.append("5) OBSERVATIONS — Voice:\n");
        sb.append(" • Use the volume, pitch, and spectral features to give empathetic interpretations. For example, 'a very quiet voice can sometimes mean someone is feeling drained or tentative.' Provide 1–2 supportive interpretations.\n\n");

        sb.append("6) FLAGGED_EXCERPTS:\n");
        sb.append(" • Quote exact concerning transcript lines. Avoid paraphrasing. If none exist, write '- NONE'.\n\n");

        sb.append("7) ACTIONABLE_HIGHLIGHTS:\n");
        sb.append(" • Suggest 1–3 practical, supportive steps tied to the observations. Each should explain briefly *why* it’s helpful, so it feels encouraging rather than generic. (e.g., 'Try gentle breathing… this can help rebalance your nervous system when variability is low.').\n\n");

        sb.append("FORMAT RULES:\n");
        sb.append(" • Use the exact header names above.\n");
        sb.append(" • Lists must be numbered (1., 2.) or bulleted with '-'.\n");
        sb.append(" • Do NOT output JSON. Do NOT add extra commentary before headers.\n");
        sb.append(" • End with: STRUCTURED_SECTION_END\n\n");

        sb.append("TONE: Warm, empathetic, coaching. Always explain what the data *means for the person*, not just restating numbers.\n");

        sb.append("\nADDITIONAL_OUTPUT (after STRUCTURED_SECTION_END):\n");
        sb.append(" • After the text ends with STRUCTURED_SECTION_END, on the very next line output a single CSV-style line prefixed with 'SPIDER_DATA:' containing six integers (0-100) for the axes in this exact order: Stress, LowMood, SocialWithdrawal, Irritability, CognitiveFatigue, Arousal. Example: SPIDER_DATA:72,34,12,22,40,65\n");
        sb.append(" • Immediately after those six integers append ',CONF:' and a single integer (0-100) representing overall confidence. Example: SPIDER_DATA:72,34,12,22,40,65,CONF:82\n");
        sb.append(" • That single line must contain ONLY the prefix and numbers (no JSON, no extra words, no explanation). Do NOT change or repeat the structured sections above.\n");


        return sb.toString();
    }


}
