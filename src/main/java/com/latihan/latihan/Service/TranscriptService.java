package com.latihan.latihan.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.latihan.latihan.DTO.PolishedTranscript;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;


@Service
public class TranscriptService {

    @Autowired
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public TranscriptService(WebClient webClient, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = webClient;
    }

    public PolishedTranscript polishTranscript(String rawTranscript){
        String prompt = """
        You are a transcript polishing assistant.
        Clean up grammar, remove filler words, and clarify unclear phrases while keeping meaning intact.
        Return ONLY a single JSON object and NOTHING ELSE in this exact shape (no explanation, no code fences):
        {
          "finalTranscript": "<cleaned text>",
          "edits": ["<change 1>", "<change 2>", ...]
        }
        If you cannot produce JSON, return {"finalTranscript": "<original transcript>", "edits": []}.
        Original transcript:
        """ + rawTranscript;

        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of(
                "role", "user",
                "parts", java.util.List.of(part)
        );
        Map<String, Object> generationConfig = Map.of(
                "temperature", 0,
                "maxOutputTokens", 1024
        );
        Map<String, Object> body = Map.of(
                "contents", java.util.List.of(content),
                "generationConfig", generationConfig
        );


        System.out.println("Request Body Transcript: " + body);

        Map<?,?> aiResponse = webClient.post()
                .uri("/v1beta/models/gemini-2.0-flash:generateContent")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        try {
            var candidates = (java.util.List<Map<String, Object>>) aiResponse.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                var responseContent = (Map<String, Object>) candidates.get(0).get("content");
                if (responseContent != null) {
                    var parts = (java.util.List<Map<String, Object>>) responseContent.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        String rawText = (String) parts.get(0).get("text");
                        // Try to extract JSON substring
                        String jsonCandidate = extractFirstJsonObject(rawText);
                        if (jsonCandidate == null) {
                            // Last attempt: try to remove triple backticks presence via regex (as fallback)
                            String cleaned = rawText.replaceAll("(?s)```\\w*\\n(.*?)\\n```", "$1").trim();
                            jsonCandidate = extractFirstJsonObject(cleaned);
                        }
                        if (jsonCandidate != null) {
                            try {
                                PolishedTranscript result = objectMapper.readValue(jsonCandidate, PolishedTranscript.class);
                                return result;
                            } catch (Exception e) {
                                // parsing failed; log and fallback
                                System.err.println("Failed to parse JSON candidate: " + jsonCandidate);
                                e.printStackTrace();
                            }
                        } else {
                            System.err.println("No JSON object found in model output. Model output:\n" + rawText);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Fallback: return raw transcript if Gemini parsing fails
        return new PolishedTranscript(rawTranscript, java.util.List.of());

    }

    private String extractFirstJsonObject(String s) {
        if (s == null) return null;
        String t = s.trim();

        // Quick strip: if the whole text is enclosed in triple backticks, extract inside
        if (t.startsWith("```")) {
            int end = t.indexOf("```", 3);
            if (end > 3) {
                // remove leading ```lang? and trailing ```
                // if there are multiple fences, find last fence
                int lastFence = t.lastIndexOf("```");
                if (lastFence > 3) {
                    t = t.substring(3, lastFence).trim();
                    // If first line is a language token (e.g. "json"), remove it
                    int nl = t.indexOf('\n');
                    if (nl > 0 && t.substring(0, nl).matches("\\w+")) {
                        t = t.substring(nl + 1).trim();
                    }
                }
            }
        }

        // Strip single/backtick wrappers like `...`
        if ((t.startsWith("`") && t.endsWith("`")) || (t.startsWith("\"") && t.endsWith("\""))) {
            t = t.substring(1, t.length() - 1).trim();
        }

        // Now find first '{' and extract balanced braces (basic but robust for typical model outputs)
        int start = t.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < t.length(); i++) {
            char c = t.charAt(i);
            // naive string detection to avoid counting braces inside quotes
            if (c == '"' && (i == 0 || t.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        // found closing brace for the first object
                        return t.substring(start, i + 1).trim();
                    }
                }
            }
        }
        // not balanced
        return null;
    }
}
