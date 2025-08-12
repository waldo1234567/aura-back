package com.latihan.latihan.DTO;

import lombok.Data;

@Data
public class ExpressionPoint {
    private long time;
    private String emotion;
    private double confidence;
    private double faceConfidence;
    private double ear;
    private double blinkRate;
}
