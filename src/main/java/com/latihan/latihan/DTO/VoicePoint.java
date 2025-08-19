package com.latihan.latihan.DTO;

import lombok.Data;

import java.util.List;

@Data
public class VoicePoint {
    private long time;
    private double volume;
    private Double pitch;
    private List<Double> mfcc;
    private double spectralCentroid;
    private double zcr;
    private boolean isValid;
}
