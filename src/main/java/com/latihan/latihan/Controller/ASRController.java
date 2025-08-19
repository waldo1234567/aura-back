package com.latihan.latihan.Controller;
import com.latihan.latihan.DTO.PolishedTranscript;
import com.latihan.latihan.Service.TranscriptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@RequestMapping("/api/v1/asr")
public class ASRController {

    @Autowired
    private final TranscriptService transcriptService;


    public ASRController(TranscriptService transcriptService) {
        this.transcriptService = transcriptService;
    }


    @PostMapping("/polish")
    public ResponseEntity<PolishedTranscript> polish(@RequestBody Map<String, String> payload){
        String transcript = payload.get("raw");
        if(transcript == null) return ResponseEntity.badRequest().build();
        PolishedTranscript p = transcriptService.polishTranscript(transcript);
        System.out.println("Returning PolishedTranscript: " + p);
        return ResponseEntity.ok(p);
    }
}
