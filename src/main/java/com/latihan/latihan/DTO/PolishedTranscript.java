package com.latihan.latihan.DTO;

import lombok.Data;

import java.util.List;

@Data
public class PolishedTranscript {
    private String finalTranscript;
    private List<String> edits;

    public PolishedTranscript() {}
    public PolishedTranscript(String finalTranscript, List<String> edits) {
        this.finalTranscript = finalTranscript;
        this.edits = edits;
    }

    @Override
    public String toString() {
        return "PolishedTranscript{" +
                "finalTranscript='" + finalTranscript + '\'' +
                ", edits=" + edits +
                '}';
    }
}
