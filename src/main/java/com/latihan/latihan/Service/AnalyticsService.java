package com.latihan.latihan.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.latihan.latihan.DTO.ExpressionPoint;
import com.latihan.latihan.DTO.HrPoint;
import com.latihan.latihan.DTO.TimelineEntry;
import com.latihan.latihan.DTO.VoicePoint;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

        List<VoicePoint> valid = vpts.stream()
                .filter(v -> Boolean.TRUE.equals(v.isValid())) // explicit check
                .toList();

        if (valid.isEmpty()) {
            return Map.of("avgVolume", 0.0, "avgPitch", 0.0);
        }

        double avgVol = valid.stream().mapToDouble(VoicePoint::getVolume).average().orElse(0);
        double maxVol = valid.stream().mapToDouble(VoicePoint::getVolume).max().orElse(0);

        double avgPitch = valid.stream()
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
        List<VoicePoint> valid = vpts.stream()
                .filter(v -> Boolean.TRUE.equals(v.isValid()))
                .filter(v -> v.getMfcc() != null && !v.getMfcc().isEmpty())
                .toList();
        if (valid.isEmpty() || valid.get(0).getMfcc() == null || valid.get(0).getMfcc().isEmpty()) {
            return Map.of("avgMfcc", List.of(), "avgSpectralCentroid", 0.0, "avgZcr", 0.0);
        }

        int mfccCount = valid.get(0).getMfcc().size();
        System.out.println(mfccCount + "mfcc count");
        List<Double> avgMfcc = new ArrayList<>(mfccCount);
        System.out.println("Voice points: " + vpts.size());
        if (!valid.isEmpty()) {
            System.out.println("First MFCC: " + vpts.get(0).getMfcc());
        }
        for (int i = 0; i < mfccCount; i++){
            final int idx = i;
            double mean = valid.stream()
                    .map(VoicePoint::getMfcc)
                    .mapToDouble(mfccList -> mfccList.get(idx))
                    .average().orElse(0);
            avgMfcc.add(mean);
        }

        double avgSpect = valid.stream().mapToDouble(VoicePoint::getSpectralCentroid).average().orElse(0);

        double avgZcr = valid.stream().mapToDouble(VoicePoint::getZcr).average().orElse(0);

        return Map.of(
                "avgMfcc",           avgMfcc,
                "avgSpectralCentroid", avgSpect,
                "avgZcr",           avgZcr
        );
    }

    public Map<String, Double> computeFrequencyHrv(List<HrPoint> readings){
        if (readings == null || readings.size() < 4) {
            return Map.of("LF", 0.0, "HF", 0.0, "LF/HF", 0.0);
        }

        Map<Long, Double> lastPerTs = new LinkedHashMap<>();
        for (HrPoint r : readings) {
            Double bpm = r.getBpm();
            if (bpm == null || bpm <= 0) continue;
            long rawT = r.getTime();
            long tMs = (rawT > 1_000_000_000_000L) ? rawT : (long) (rawT * 1000.0); // support seconds or ms
            lastPerTs.put(tMs, bpm); // keeps last for same timestamp
        }

        if (lastPerTs.size() < 4) {
            return Map.of("LF", 0.0, "HF", 0.0, "LF/HF", 0.0, "valid", 0.0);
        }

        // --- 2) build filtered lists: remove duplicate/non-positive dt and huge gaps (>2000 ms)
        List<Long> timesMsAll = new ArrayList<>(lastPerTs.keySet());
        List<Double> bpmsAll   = new ArrayList<>(lastPerTs.values());

        List<Long> timesMs = new ArrayList<>();
        List<Double> rrSec  = new ArrayList<>(); // seconds

        long prevT = -1;
        for (int i = 0; i < timesMsAll.size(); i++) {
            long t = timesMsAll.get(i);
            double bpm = bpmsAll.get(i);
            if (prevT == -1) {
                // always accept first
                timesMs.add(t);
                rrSec.add(60.0 / bpm);
                prevT = t;
            } else {
                long dt = t - prevT;
                if (dt <= 0 || dt > 2000) {
                    continue;
                } else {
                    timesMs.add(t);
                    rrSec.add(60.0 / bpm);
                    prevT = t;
                }
            }
        }

        if (timesMs.size() < 4) {
            return Map.of("LF", 0.0, "HF", 0.0, "LF/HF", 0.0, "valid", 0.0);
        }

        // convert times to seconds relative to start
        double t0s = timesMs.get(0) / 1000.0;
        List<Double> timesSec = new ArrayList<>(timesMs.size());
        for (Long tm : timesMs) timesSec.add((tm / 1000.0) - t0s);

        double duration = timesSec.get(timesSec.size() - 1) - timesSec.get(0);
        if (duration < 60.0) { // require at least 60s for freq-HRV
            return Map.of("LF", 0.0, "HF", 0.0, "LF/HF", 0.0, "valid", 0.0);
        }

        // --- 3) resample onto uniform grid at fs (Hz)
        double fs = 4.0; // 4 Hz
        int N = (int)Math.round(Math.max(256, Math.floor(fs * duration)));
        double dt = 1.0 / fs;
        double startT = timesSec.get(0);
        double[] resampled = new double[N];
        double[] timesArr = timesSec.stream().mapToDouble(d -> d).toArray();
        double[] rrArr = rrSec.stream().mapToDouble(d -> d).toArray();

        for (int i = 0; i < N; i++) {
            double tt = startT + i * dt;
            int idx = Arrays.binarySearch(timesArr, tt);
            if (idx >= 0) {
                resampled[i] = rrArr[idx];
            } else {
                int ins = -idx - 1;
                if (ins == 0) {
                    resampled[i] = rrArr[0];
                } else if (ins >= timesArr.length) {
                    resampled[i] = rrArr[rrArr.length - 1];
                } else {
                    int lo = ins - 1;
                    int hi = ins;
                    double frac = (tt - timesArr[lo]) / (timesArr[hi] - timesArr[lo]);
                    resampled[i] = rrArr[lo] * (1 - frac) + rrArr[hi] * frac;
                }
            }
        }

        // Detrend (remove mean)
        double mean = Arrays.stream(resampled).average().orElse(0.0);
        for (int i = 0; i < resampled.length; i++) resampled[i] -= mean;

        // pad to power of two
        int L = 1;
        while (L < resampled.length) L <<= 1;
        double[] padded = Arrays.copyOf(resampled, L);

        // FFT
        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] spec = fft.transform(padded, TransformType.FORWARD);
        double df = fs / (double) padded.length;

        double lf = 0.0, hf = 0.0;
        int half = padded.length / 2;
        for (int i = 1; i < half; i++) {
            double freq = i * df;
            double power = spec[i].abs() * spec[i].abs();
            if (freq >= 0.04 && freq < 0.15) lf += power;
            if (freq >= 0.15 && freq < 0.4) hf += power;
        }

        double ratio = (hf <= 0) ? Double.POSITIVE_INFINITY : lf / hf;
        return Map.of("LF", lf, "HF", hf, "LF/HF", ratio, "valid", 1.0);
    }


    public void diagnoseAndComputeHrMetrics(List<HrPoint> readings) {
        if (readings == null || readings.isEmpty()) {
            System.out.println("no hr readings");
            return;
        }

        Map<Long, Double> lastPerTs = new LinkedHashMap<>();
        for (HrPoint h : readings) {
            Double bpm = h.getBpm();
            if (bpm == null || h.getBpm() <= 0) continue;
            long tMs = h.getTime() > 1_000_000_000_000L ? h.getTime() : (long)(h.getTime() * 1000.0);
            lastPerTs.put(tMs, h.getBpm());
        }
        List<Long> timesMs = new ArrayList<>(lastPerTs.keySet());
        List<Double> rrMsRaw = lastPerTs.values().stream()
                .map(b -> 60.0 / b * 1000.0) // <-- CORRECT: 60/bpm -> seconds, *1000 -> ms
                .collect(Collectors.toList());

// Filter bad dt and build rrMsFiltered
        List<Double> rrMsFiltered = new ArrayList<>();
        List<Long> tFilt = new ArrayList<>();
        if (!timesMs.isEmpty()) {
            tFilt.add(timesMs.get(0));
            rrMsFiltered.add(rrMsRaw.get(0));
            for (int i = 1; i < timesMs.size(); i++) {
                long dt = timesMs.get(i) - timesMs.get(i-1);
                if (dt <= 0 || dt > 2000) continue; // skip duplicates or huge gaps
                tFilt.add(timesMs.get(i));
                rrMsFiltered.add(rrMsRaw.get(i));
            }
        }

        if (rrMsFiltered.size() >= 2) {
            int M = rrMsFiltered.size();
            double mean = rrMsFiltered.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double sdnn = Math.sqrt(rrMsFiltered.stream().mapToDouble(x -> Math.pow(x - mean, 2)).sum() / (M - 1));

            double sumDiffSq = 0;
            int cnt50 = 0;
            for (int i = 0; i < M - 1; i++) {
                double diff = rrMsFiltered.get(i + 1) - rrMsFiltered.get(i);
                sumDiffSq += diff * diff;
                if (Math.abs(diff) > 50) cnt50++;
            }
            double rmssd = Math.sqrt(sumDiffSq / (double)(M - 1));
            double pnn50 = (M > 1) ? cnt50 / (double)(M - 1) * 100.0 : 0.0;

            System.out.printf("Filtered SDNN(ms)=%.3f RMSSD(ms)=%.3f pNN50=%.2f%%%n", sdnn, rmssd, pnn50);
            System.out.printf("RR mean(ms)=%.3f RR std(ms)=%.3f count=%d%n", mean, Math.sqrt(rrMsFiltered.stream().mapToDouble(x -> Math.pow(x-mean,2)).sum()/(M-1)), M);
        } else {
            System.out.println("Too few filtered RR samples after dedupe/filter.");
        }
    }
    public Map<String,Object> computeRiskSummary(Map<String,Object> faceMetrics,
                                                 Map<String, Double> hrvMetrics,
                                                 Map<String, Double> voiceMetrics,
                                                 String transcript
                                                 ){
        double score = 0.0;

        Double ssdn = getDouble(hrvMetrics.get("SDNN"));
        Double rmssd = getDouble(hrvMetrics.get("RMSSD"));
        System.out.println(ssdn + "=> ssdn on risk summary");
        System.out.println(rmssd + "=> rmssd on risk summary");
        if(ssdn != null){
            score += Math.max(0, (50.0 - ssdn)) * 0.8;
        }
        if (rmssd != null) {
            score += Math.max(0, (30.0 - rmssd)) * 1.0;
        }
        System.out.println(score + "=>voice score in risk summary");

        Double sad = getDouble(faceMetrics.get("sadness"));
        Double disgust = getDouble(faceMetrics.get("disgusted"));
        if (sad != null) score += sad * 50;        // e.g., sadness=0.3 -> +15
        if (disgust != null) score += disgust * 20;

        Double avgVolume = getDouble(voiceMetrics.get("avg_volume"));
        Double avgPitch = getDouble(voiceMetrics.get("avg_pitch"));
        if (avgVolume != null && avgVolume < 0.01) score += 5;
        if (avgPitch != null && avgPitch > 200) score += 5;

        List<String> dangerKeywords = List.of("suicide","kill myself","i can't go on","don't want to live","end my life","hopeless");
        String lower = transcript == null ? "" : transcript.toLowerCase();
        for (String k : dangerKeywords) if (lower.contains(k)) score += 30;

        double finalScore = Math.max(0.0, Math.min(100.0, score));

        String level = finalScore >= 80 ? "EMERGENCY" : finalScore >= 60 ? "HIGH" : finalScore >= 40 ? "MEDIUM" : "LOW";

        String explanation = switch (level) {
            case "EMERGENCY" -> "High-risk language detected and physiological markers indicate high stress.";
            case "HIGH" -> "Significant stress markers or concerning language detected.";
            case "MEDIUM" -> "Moderate stress markers observed.";
            default -> "No significant stress markers detected.";
        };

        Map<String, Object> out = new HashMap<>();
        out.put("score", Math.round(finalScore));
        out.put("level", level);
        out.put("explanation", explanation);
        System.out.println(out + "==> risk summary output");
        return out;
    }

    public Map<String,Object> computeDiagnosticsFromTimeline(List<TimelineEntry> timeline, String transcript) {
        Map<String,Object> out = new HashMap<>();
        if (timeline == null || timeline.isEmpty()) {
            out.put("beatsUsed", 0);
            out.put("duration_s", 0.0);
            out.put("audioValidFraction", 0.0);
            out.put("faceConfidence", 0.0);
            out.put("transcriptWords", transcript == null ? 0 : transcript.split("\\s+").length);
            out.put("meanBpm", 0.0);
            out.put("voiceActivityFrac", 0.0);
            out.put("longPauseFrac", 0.0);
            out.put("pitchVar", 0.0);
            out.put("maxPitch", 0.0);
            return out;
        }

        // duration
        List<Long> times = timeline.stream()
                .map(TimelineEntry::getTime)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
        double duration_s = (times.get(times.size()-1) - times.get(0)) / 1000.0;

        // HR dedupe (keep last per timestamp)
        Map<Long, Double> lastPerTs = new LinkedHashMap<>();
        for (TimelineEntry e : timeline) {
            HrPoint hr = e.getHr();
            Double bpm = e.getHr().getBpm();
            if (hr != null && bpm != null && hr.getBpm() > 0) {
                lastPerTs.put(hr.getTime(), hr.getBpm());
            }
        }
        List<Double> bpms = new ArrayList<>(lastPerTs.values());
        int beatsUsed = bpms.size();
        double meanBpm = beatsUsed > 0 ? bpms.stream().mapToDouble(Double::doubleValue).average().orElse(0.0) : 0.0;

        // Voice aggregates
        List<VoicePoint> voices = timeline.stream()
                .map(TimelineEntry::getVoice)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        long validCount = voices.stream()
                .filter(v -> Boolean.TRUE.equals(v.isValid()) || Boolean.TRUE.equals(v.isValid()))
                .count();
        double audioValidFraction = voices.isEmpty() ? 0.0 : (validCount / (double) voices.size());
        double voiceActivityFrac = timeline.size() > 0 ? (voices.size() / (double) timeline.size()) : 0.0;

        // pitch stats
        List<Double> pitches = voices.stream()
                .map(VoicePoint::getPitch)
                .filter(Objects::nonNull)
                .filter(p -> p > 0)
                .collect(Collectors.toList());
        double maxPitch = pitches.isEmpty() ? 0.0 : pitches.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double pitchVar = 0.0;
        if (pitches.size() > 1) {
            double meanP = pitches.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            pitchVar = pitches.stream().mapToDouble(p -> Math.pow(p - meanP, 2)).sum() / (pitches.size() - 1);
        }

        // longPauseFrac (voice time gaps > 2000 ms)
        List<Long> vTimes = voices.stream().map(VoicePoint::getTime).filter(Objects::nonNull).sorted().collect(Collectors.toList());
        int longGaps = 0;
        for (int i = 1; i < vTimes.size(); i++) if (vTimes.get(i) - vTimes.get(i-1) > 2000) longGaps++;
        double longPauseFrac = vTimes.size() > 1 ? (longGaps / (double)(vTimes.size() - 1)) : 0.0;

        // face confidence
        List<Double> faceConfs = timeline.stream()
                .map(TimelineEntry::getExpression)
                .filter(Objects::nonNull)
                .map(ExpressionPoint::getConfidence)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).boxed().collect(Collectors.toList());
        double faceConfidence = faceConfs.isEmpty() ? 0.0 : faceConfs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        int transcriptWords = transcript == null ? 0 : transcript.trim().isEmpty() ? 0 : transcript.trim().split("\\s+").length;

        out.put("beatsUsed", beatsUsed);
        out.put("duration_s", duration_s);
        out.put("audioValidFraction", audioValidFraction);
        out.put("faceConfidence", faceConfidence);
        out.put("transcriptWords", transcriptWords);
        out.put("meanBpm", meanBpm);
        out.put("voiceActivityFrac", voiceActivityFrac);
        out.put("longPauseFrac", longPauseFrac);
        out.put("pitchVar", pitchVar);
        out.put("maxPitch", maxPitch);

        return out;
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> computeSpiderScores(
            Map<String,Object> face,
            Map<String,Double> hrvTime,
            Map<String,Double> hrvFreq,
            Map<String,Double> voiceTime,
            Map<String,Object> voiceAdv,
            Map<String,Object> diagnostics
    ) {
        // safe getters
        double lf_hf = getDoubleSafe(hrvFreq != null ? hrvFreq.get("LF/HF") : null);
        double rmssd = getDoubleSafe(hrvTime != null ? hrvTime.get("RMSSD") : null);
        double sdnn  = getDoubleSafe(hrvTime != null ? hrvTime.get("SDNN") : null);
        double meanBpm = getDoubleSafe(diagnostics != null ? diagnostics.get("meanBpm") : null);
        double avgPitch = getDoubleSafe(voiceTime != null ? voiceTime.get("avgPitch") : null);
        double avgVol = getDoubleSafe(voiceTime != null ? voiceTime.get("avgVolume") : null);

        Map<String, Double> percentTime = new HashMap<>();
        if (face != null && face.get("percentTime") instanceof Map) {
            try {
                Map<?,?> pt = (Map<?,?>) face.get("percentTime");
                pt.forEach((k,v) -> percentTime.put(String.valueOf(k), getDoubleSafe(v)));
            } catch (ClassCastException ignored) { /* fallback to empty */ }
        }
        double sadPct = percentTime.getOrDefault("sad", 0.0);
        double angryPct = percentTime.getOrDefault("angry", 0.0);
        double happyPct = percentTime.getOrDefault("happy", 0.0);

        // diagnostics
        double beatsUsed = getDoubleSafe(diagnostics != null ? diagnostics.get("beatsUsed") : null);
        double audioValidFraction = getDoubleSafe(diagnostics != null ? diagnostics.get("audioValidFraction") : null);
        double faceConf = getDoubleSafe(diagnostics != null ? diagnostics.get("faceConfidence") : null);
        double transcriptWords = getDoubleSafe(diagnostics != null ? diagnostics.get("transcriptWords") : null);

        // normalized helpers (0..1)
        java.util.function.DoubleUnaryOperator norm = (v) -> 0.0; // placeholder not used
        // use explicit method below: normVal(value, min, max)

        // confidence (0..1)
        double confRaw = 0.4 * Math.min(1.0, beatsUsed / 120.0)
                + 0.3 * clamp01(audioValidFraction)
                + 0.2 * clamp01(faceConf)
                + 0.1 * Math.min(1.0, transcriptWords / 30.0);
        double conf = clamp01(confRaw);

        // Axis formulas (0..1)
        double stressScore = clamp01(
                0.4 * normVal(lf_hf, 0.0, 20.0)
                        + 0.3 * (1.0 - normVal(rmssd, 0.0, 50.0))
                        + 0.2 * normVal(meanBpm, 40.0, 150.0)
                        + 0.1 * clamp01(angryPct)
        );

        double lowMoodScore = clamp01(
                0.35 * clamp01(sadPct)
                        + 0.25 * (1.0 - normVal(avgPitch, 50.0, 300.0))
                        + 0.20 * (1.0 - normVal(avgVol, 0.0, 0.5))
                        + 0.20 * (1.0 - normVal(sdnn, 0.0, 60.0))
        );

        double voiceActivityFrac = getDoubleSafe(diagnostics != null ? diagnostics.getOrDefault("voiceActivityFrac", 0.0) : 0.0);
        double socialScore = clamp01(
                0.4 * (1.0 - clamp01(voiceActivityFrac))
                        + 0.3 * (1.0 - normVal(avgVol, 0.0, 0.5))
                        + 0.3 * (1.0 - clamp01(happyPct))
        );

        double maxPitch = getDoubleSafe(diagnostics != null ? diagnostics.getOrDefault("maxPitch", 0.0) : 0.0);
        double irritability = clamp01(
                0.5 * clamp01(angryPct)
                        + 0.3 * normVal(maxPitch, 50.0, 300.0)
                        + 0.2 * normVal(lf_hf, 0.0, 20.0)
        );

        double longPauseFrac = getDoubleSafe(diagnostics != null ? diagnostics.getOrDefault("longPauseFrac", 0.0) : 0.0);
        double avgZcr = getDoubleSafe(voiceAdv != null ? voiceAdv.getOrDefault("avgZcr", 0.0) : null);
        double pitchVar = getDoubleSafe(diagnostics != null ? diagnostics.getOrDefault("pitchVar", 0.0) : 0.0);

        double cognitiveFatigue = clamp01(
                0.4 * clamp01(longPauseFrac)
                        + 0.3 * (1.0 - clamp01(avgZcr / 2000.0))
                        + 0.3 * (1.0 - normVal(pitchVar, 0.0, 100.0))
        );

        double spectralCentroid = getDoubleSafe(voiceAdv != null ? voiceAdv.getOrDefault("avgSpectralCentroid", 0.0) : 0.0);
        double arousal = clamp01(
                0.5 * normVal(meanBpm, 40.0, 150.0)
                        + 0.3 * normVal(lf_hf, 0.0, 20.0)
                        + 0.2 * clamp01(spectralCentroid / 5000.0)
        );

        // scale to 0..100 and apply confidence weighting (conf 0..1)
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("Stress", (int) Math.round(stressScore * 100.0 * conf));
        out.put("LowMood", (int) Math.round(lowMoodScore * 100.0 * conf));
        out.put("SocialWithdrawal", (int) Math.round(socialScore * 100.0 * conf));
        out.put("Irritability", (int) Math.round(irritability * 100.0 * conf));
        out.put("CognitiveFatigue", (int) Math.round(cognitiveFatigue * 100.0 * conf));
        out.put("Arousal", (int) Math.round(arousal * 100.0 * conf));
        out.put("confidence", (int) Math.round(conf * 100.0));

        return out;
    }

    // helper: clamp to 0..1 safely
    private static double clamp01(double v) {
        if (Double.isFinite(v)) {
            return Math.max(0.0, Math.min(1.0, v));
        } else {
            return 0.0;
        }
    }

    // helper: normalize value to [0,1] given min/max (handles NaN/null)
    private static double normVal(double v, double min, double max) {
        if (!Double.isFinite(v)) return 0.0;
        if (max <= min) return 0.0;
        double t = (v - min) / (max - min);
        return Math.max(0.0, Math.min(1.0, t));
    }

    // safe number extraction (handles Number types, Strings, null)
    private static double getDoubleSafe(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            if (Double.isFinite(d)) return d;
            return 0.0;
        }
        try {
            String s = String.valueOf(o);
            if (s.isEmpty()) return 0.0;
            double d = Double.parseDouble(s);
            if (Double.isFinite(d)) return d;
        } catch (Exception ignored) {}
        return 0.0;
    }

    public Map<String,Object> parseAiReplyForStructuredData(String aiReply, ObjectMapper mapper) {
        List<Map<String,Object>> flagged = new ArrayList<>();
        List<Map<String,Object>> actions = new ArrayList<>();

        // possible header variants
        String[] flaggedHeaders = new String[] {
                "flagged excerpts", "flagged excerpt", "flagged passages", "flagged items", "flagged:", "FLAGGED_EXCERPTS:"
        };
        String[] actionHeaders = new String[] {
                "actionable highlights", "actionable items", "actionable highlight", "action items", "actions", "actionable:", "ACTIONABLE_HIGHLIGHTS:"
        };

        // try flagged section
        String flaggedBlock = null;
        for (String h : flaggedHeaders) {
            flaggedBlock = extractSectionBlock(aiReply, Pattern.quote(h));
            if (flaggedBlock != null) break;
        }
        if (flaggedBlock != null) {
            List<String> lines = parseBulletListBlock(flaggedBlock);
            for (String l : lines) {
                Map<String,Object> item = new HashMap<>();
                item.put("text", l);
                item.put("keyword", null);
                item.put("confidence", 0.9);
                item.put("timestampMs", null);
                flagged.add(item);
            }
        }

        // try actionable section
        String actionBlock = null;
        for (String h : actionHeaders) {
            actionBlock = extractSectionBlock(aiReply, Pattern.quote(h));
            if (actionBlock != null) break;
        }
        if (actionBlock != null) {
            List<String> lines = parseBulletListBlock(actionBlock);
            int id = 1;
            for (String l : lines) {
                Map<String,Object> act = new HashMap<>();
                act.put("id", "a" + id++);
                act.put("text", l);
                act.put("status", "pending");
                act.put("confidence", 0.8);
                actions.add(act);
            }
        }

        // Return both lists (may be empty)
        Map<String,Object> out = new HashMap<>();
        out.put("flaggedExcerpts", flagged);
        out.put("actionableHighlights", actions);
        return out;
    }

    public String detectLanguage(String text) {
        if (text == null) return "unknown";
        String lower = text.toLowerCase();
        if (lower.matches(".*\\b(aku|saya|kamu|tidak|sudah|belum)\\b.*")) return "id"; // Indonesian heuristics
        if (lower.matches(".*\\b(je|le|la|est|une)\\b.*")) return "fr";
        if (lower.matches(".*[\\p{IsHan}]")) return "zh";
        // fallback
        return "en";
    }

    public String translateToEnglish(String text) {
        // For hackathon: return original with suffix. Replace with real API or LLM call later.
        if (text == null) return "";
        return text + " (translated→en)";
    }

    //-----------------helpers----------------------
    private Double getDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
    }

    private static List<String> parseBulletListBlock(String block) {
        List<String> lines = new ArrayList<>();
        if (block == null) return lines;
        // split into lines and accept bullets starting with -, *, • or leading number
        String[] raw = block.split("\\r?\\n");
        for (String r : raw) {
            String t = r.trim();
            if (t.isEmpty()) continue;
            // remove leading bullet/number markers
            t = t.replaceFirst("^([\\-\\*•]\\s*)", "").replaceFirst("^\\d+\\.\\s*", "").trim();
            if (!t.isEmpty()) lines.add(t);
        }
        return lines;
    }

    private static String extractSectionBlock(String aiText, String headerRegex) {
        if (aiText == null) return null;
        // (?is) -> DOTALL + CASE_INSENSITIVE. Capture the header and following block until two consecutive newlines
        Pattern p = Pattern.compile("(?is)" + headerRegex + "\\s*[:\\-]?\\s*(.+?)(?=(\\n\\s*\\n)|$)");
        Matcher m = p.matcher(aiText);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }


}
