package com.latihan.latihan.Service;

import com.latihan.latihan.DTO.ExpressionPoint;
import com.latihan.latihan.DTO.HrPoint;
import com.latihan.latihan.DTO.VoicePoint;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {
    public Map<String,Object> summarizeFace(List<ExpressionPoint> frames){
        Map<String, Object> out = new HashMap<>();
        if(frames == null || frames.isEmpty()){
            out.put("percentTime", Map.of());
            out.put("avgConfidence", 0.0);
            return out;
        }

        int total = frames.size();
        Map<String, Long> counts = frames.stream().
                collect(Collectors.groupingBy(ExpressionPoint::getEmotion, Collectors.counting()));
        Map<String, Double> percentTimes = new HashMap<>();
        counts.forEach((emo,cnt) -> percentTimes.put(emo, cnt/ (double) total));

        double avgConfidence = frames.stream()
                .mapToDouble(ExpressionPoint::getConfidence).average().orElse(0);
        out.put("percentTime", percentTimes);
        out.put("avgConfidence", avgConfidence);
        return out;
    }

    public Map<String,Double> computeHrv(List<HrPoint> readings){
        if (readings == null || readings.size() < 2) {
            return Map.of("SDNN", 0.0, "RMSSD", 0.0, "pNN50", 0.0);
        }

        List<Double> rr = readings.stream()
                .map(r -> r.getBpm())
                .filter(bpm -> bpm != null && bpm > 0)
                .map(bpm -> 60000.0 / bpm)
                .collect(Collectors.toList());

        int N = rr.size();
        if (N < 2) {
            return Map.of("SDNN", 0.0, "RMSSD", 0.0, "pNN50", 0.0);
        }

        double mean = rr.stream().mapToDouble(d-> d).average().orElse(0);
        double sumSq = rr.stream().mapToDouble(d -> Math.pow(d - mean, 2)).sum();
        double sdnn = Math.sqrt(sumSq / (N - 1));

        double sumDiffSq = 0;
        int cnt50 = 0;
        for (int i = 0; i < N - 1; i++) {
            double diff = rr.get(i + 1) - rr.get(i);
            sumDiffSq += diff * diff;
            if (Math.abs(diff) > 50) cnt50++;
        }
        double rmssd = Math.sqrt(sumDiffSq / (N - 1));
        double pnn50 = N > 1 ? cnt50 / (double)(N - 1) * 100 : 0.0;

        return Map.of(
                "SDNN",  sdnn,
                "RMSSD", rmssd,
                "pNN50", pnn50
        );

    }

    public Map<String,Double> summarizeVoice(List<VoicePoint> vpts){
        if (vpts == null || vpts.isEmpty()) {
            return Map.of("avgVolume", 0.0, "maxVolume", 0.0, "avgPitch", 0.0);
        }
        double avgVol = vpts.stream().mapToDouble(VoicePoint::getVolume).average().orElse(0);
        double maxVol = vpts.stream().mapToDouble(VoicePoint::getVolume).max().orElse(0);

        double avgPitch = vpts.stream()
                .map(VoicePoint::getPitch)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .filter(p -> p > 0)            // exclude -1 or zero (no pitch)
                .average()
                .orElse(0);

        return Map.of(
                "avgVolume", avgVol,
                "maxVolume", maxVol,
                "avgPitch", avgPitch
        );
    }

    public Map<String,Double> summarizeFaceAdv(List<ExpressionPoint> pts){
        double avgEar = pts.stream().mapToDouble(ExpressionPoint::getEar).average().orElse(0);
        return Map.of(
                "avgEar", avgEar
        );
    }

    public Map<String,Object> summarizeVoiceAdv(List<VoicePoint> vpts){
        if (vpts == null || vpts.isEmpty()) {
            return Map.of("avgMfcc", List.of(), "avgSpectralCentroid", 0.0, "avgZcr", 0.0);
        }
        int mfccCount = vpts.get(0).getMfcc().size();
        System.out.println(mfccCount + "mfcc count");
        List<Double> avgMfcc = new ArrayList<>(mfccCount);
        System.out.println("Voice points: " + vpts.size());
        if (!vpts.isEmpty()) {
            System.out.println("First MFCC: " + vpts.get(0).getMfcc());
        }
        for (int i = 0; i < mfccCount; i++){
            final int idx = i;
            double mean = vpts.stream()
                    .map(VoicePoint::getMfcc)
                    .mapToDouble(mfccList -> mfccList.get(idx))
                    .average().orElse(0);
            avgMfcc.add(mean);
        }

        double avgSpect = vpts.stream().mapToDouble(VoicePoint::getSpectralCentroid).average().orElse(0);

        double avgZcr = vpts.stream().mapToDouble(VoicePoint::getZcr).average().orElse(0);

        return Map.of(
                "avgMfcc",           avgMfcc,
                "avgSpectralCentroid", avgSpect,
                "avgZcr",           avgZcr
        );
    }

    public Map<String, Double> computeFrequencyHrv(List<HrPoint> readings){
        if (readings == null || readings.size() < 3) {
            return Map.of("LF", 0.0, "HF", 0.0, "LF/HF", 0.0);
        }

        int N0 = readings.size();
        double[] times = new double[N0];
        double[] rrSec = new double[N0];

        long t0 = readings.get(0).getTime(); // ms
        for (int i = 0; i < N0; i++) {
            HrPoint r = readings.get(i);
            times[i] = (r.getTime() - t0) / 1000.0; // seconds since start
            double bpm = r.getBpm();
            rrSec[i] = (bpm > 0) ? (60.0 / bpm) : Double.NaN;
        }

        // remove invalid entries
        List<Integer> validIdx = new ArrayList<>();
        for (int i = 0; i < N0; i++) {
            if (Double.isFinite(rrSec[i]) && rrSec[i] > 0 && Double.isFinite(times[i])) validIdx.add(i);
        }
        if (validIdx.size() < 3) {
            return Map.of("LF", 0.0, "HF", 0.0, "LF/HF", 0.0);
        }

        double[] tValid = validIdx.stream().mapToDouble(i -> times[i]).toArray();
        double[] rrValid = validIdx.stream().mapToDouble(i -> rrSec[i]).toArray();

        // Resample onto uniform grid at fs (Hz)
        double fs = 4.0; // 4 Hz sample rate for interpolated RR series
        double totalTime = tValid[tValid.length - 1] - tValid[0];
        if (totalTime <= 0.5) { // too short to compute PSD reliably
            return Map.of("LF", 0.0, "HF", 0.0, "LF/HF", 0.0);
        }
        int N = (int)Math.round(Math.max(256, Math.floor(fs * totalTime))); // ensure some length
        double[] resampled = new double[N];
        double dt = 1.0 / fs;
        double startT = tValid[0];

        // simple linear interpolation
        for (int i = 0; i < N; i++){
            double tt = startT + i * dt;
            // find interval
            int idx = Arrays.binarySearch(tValid, tt);
            if (idx >= 0) {
                resampled[i] = rrValid[idx];
            } else {
                int ins = -idx - 1;
                if (ins == 0) {
                    resampled[i] = rrValid[0];
                } else if (ins >= tValid.length) {
                    resampled[i] = rrValid[rrValid.length - 1];
                } else {
                    int lo = ins - 1;
                    int hi = ins;
                    double frac = (tt - tValid[lo]) / (tValid[hi] - tValid[lo]);
                    resampled[i] = rrValid[lo] * (1 - frac) + rrValid[hi] * frac;
                }
            }
        }

        // Detrend (remove mean)
        double mean = Arrays.stream(resampled).average().orElse(0);
        for (int i = 0; i < resampled.length; i++) resampled[i] -= mean;

        // pad to power of two for FFT
        double[] padded = padToPowerOfTwo(resampled);

        // FFT
        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] spec = fft.transform(padded, TransformType.FORWARD);
        int L = padded.length;
        double df = fs / (double) L;

        double lf = 0.0, hf = 0.0;
        int half = L / 2;
        for (int i = 0; i < half; i++){
            double freq = i * df;
            double power = spec[i].abs() * spec[i].abs();
            if (freq >= 0.04 && freq < 0.15) lf += power;
            if (freq >= 0.15 && freq < 0.4) hf += power;
        }

        double ratio = (hf <= 0) ? Double.POSITIVE_INFINITY : lf / hf;

        return Map.of("LF", lf, "HF", hf, "LF/HF", ratio);
    }

    private double[] padToPowerOfTwo(double[] data){
        int n = data.length;
        int target = 1;
        while(target < n){
            target <<= 1;
        }
        if(target == n ) return data;
        double[] padded = Arrays.copyOf(data, target);
        return padded;
    }
}
