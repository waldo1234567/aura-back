package com.latihan.latihan.Controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.latihan.latihan.Entity.SessionEntity;
import com.latihan.latihan.Repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionRepository sessionRepository;

    @GetMapping
    public List<SessionEntity> getAllSessions() {
        return sessionRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSessionDashboard(@PathVariable Long id) throws JsonProcessingException {
        SessionEntity s = sessionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        ObjectMapper mapper = new ObjectMapper();
        Map<String,Object> resp = new HashMap<>();
        resp.put("transcript", s.getTranscript());
        resp.put("faceMetrics", mapper.readValue(s.getFaceMetrics(), Map.class));
        resp.put("hrvMetrics", mapper.readValue(s.getHrvMetrics(), Map.class));
        resp.put("voiceMetrics", mapper.readValue(s.getVoiceMetrics(), Map.class));
        resp.put("riskSummary", mapper.readValue(s.getRiskSummary(), Map.class));
        resp.put("flaggedExcerpts", mapper.readValue(s.getFlaggedExcerpts(), List.class));
        resp.put("actionableHighlights", mapper.readValue(s.getActionableHighlights(), List.class));
        resp.put("detectedLanguage", s.getDetectedLanguage());
        resp.put("translatedTranscript", s.getTranslatedTranscript());
        resp.put("audioUrl", s.getAudioUrl());
        resp.put("privacyNote", "Demo only — metrics persisted, no raw media retained.");
        return ResponseEntity.ok(resp);
    }

}
