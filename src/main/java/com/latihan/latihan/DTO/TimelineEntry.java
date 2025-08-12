package com.latihan.latihan.DTO;

import lombok.Data;

@Data
public class TimelineEntry {
    private long time;
    private ExpressionPoint expression;
    private HrPoint hr;
    private VoicePoint voice;
}
