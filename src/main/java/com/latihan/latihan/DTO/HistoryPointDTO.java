package com.latihan.latihan.DTO;

import lombok.Data;

@Data
public class HistoryPointDTO {
    public long ts;
    public Double riskScore;
    public Double SDNN;
    public Double RMSSD;
    public Double pNN50;
    public Double avgVolume;
    public Double avgPitch;
}
