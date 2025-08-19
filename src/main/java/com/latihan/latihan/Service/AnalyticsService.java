package com.latihan.latihan.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.latihan.latihan.DTO.ExpressionPoint;
import com.latihan.latihan.DTO.HrPoint;
import com.latihan.latihan.DTO.VoicePoint;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        int N0 = readings.size();
        List<Double> times = new ArrayList<>();
        List<Double> rrSec = new ArrayList<>();

        long t0 = readings.get(0).getTime(); // ms
        for (HrPoint r : readings) {
            Double bpm = r.getBpm();
            if (bpm == null || bpm <= 0) continue;
            double t = (r.getTime() - t0) / 1000.0;
            double rr = 60.0 / bpm;
            if (Double.isFinite(t) && Double.isFinite(rr) && rr > 0) {
                times.add(t);
                rrSec.add(rr);
            }
        }


        // remove invalid entries
        if (times.size() < 4) {
            return Map.of("LF", 0.0, "HF", 0.0, "LF/HF", 0.0, "valid", 0.0);
        }

        double duration = times.get(times.size() - 1) - times.get(0);
        if (duration < 60.0) { // too short to reliably estimate spectral HRV; require at least 60s (conservative)
            return Map.of("LF", 0.0, "HF", 0.0, "LF/HF", 0.0, "valid", 0.0);
        }

        double fs = 4.0; // 4 Hz
        int N = (int)Math.round(Math.max(256, Math.floor(fs * duration)));
        double dt = 1.0 / fs;
        double startT = times.get(0);
        double[] resampled = new double[N];
        for (int i = 0; i < N; i++) {
            double tt = startT + i * dt;
            // find interval
            int idx = Arrays.binarySearch(times.stream().mapToDouble(d -> d).toArray(), tt);
            if (idx >= 0) {
                resampled[i] = rrSec.get(idx);
            } else {
                int ins = -idx - 1;
                if (ins == 0) {
                    resampled[i] = rrSec.get(0);
                } else if (ins >= times.size()) {
                    resampled[i] = rrSec.get(rrSec.size() - 1);
                } else {
                    int lo = ins - 1;
                    int hi = ins;
                    double frac = (tt - times.get(lo)) / (times.get(hi) - times.get(lo));
                    resampled[i] = rrSec.get(lo) * (1 - frac) + rrSec.get(hi) * frac;
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

    public void diagnoseAndComputeHrMetrics(List<HrPoint> readings) {
        if (readings == null || readings.isEmpty()) {
            System.out.println("no hr readings");
            return;
        }
        // Build arrays, guess time units: treat getTime()>1e12 as ms, otherwise seconds -> convert to ms
        List<Long> timesMs = new ArrayList<>();
        List<Double> rrMsRaw = new ArrayList<>();
        for (HrPoint h : readings) {
            Double bpm = h.getBpm();
            if (bpm == null || bpm <= 0) continue;
            long tRaw = h.getTime();
            long tMs = tRaw > 1_000_000_000_000L ? tRaw : (long)(tRaw * 1000.0);
            timesMs.add(tMs);
            rrMsRaw.add(60.0 / bpm * 1000.0); // RR in ms
        }

        if (timesMs.size() < 2) {
            System.out.println("not enough valid bpm samples");
            return;
        }

        // Compute raw dt stats (ms)
        List<Long> dts = new ArrayList<>();
        for (int i = 0; i < timesMs.size() - 1; i++) {
            dts.add(timesMs.get(i + 1) - timesMs.get(i));
        }
        double meanDt = dts.stream().mapToLong(Long::longValue).average().orElse(Double.NaN);
        long minDt = dts.stream().mapToLong(Long::longValue).min().orElse(-1);
        long maxDt = dts.stream().mapToLong(Long::longValue).max().orElse(-1);
        System.out.printf("raw sample dt (ms): mean=%.1f min=%d max=%d count=%d%n", meanDt, minDt, maxDt, dts.size());

        // Filter out dt <= 0 and huge gaps > 2000ms
        List<Double> rrMsFiltered = new ArrayList<>();
        List<Long> timesFiltered = new ArrayList<>();
        timesFiltered.add(timesMs.get(0));
        rrMsFiltered.add(rrMsRaw.get(0));
        for (int i = 1; i < timesMs.size(); i++) {
            long dt = timesMs.get(i) - timesMs.get(i - 1);
            if (dt <= 0 || dt > 2000) {
                // skip this sample (duplicate or big gap)
                continue;
            }
            timesFiltered.add(timesMs.get(i));
            rrMsFiltered.add(rrMsRaw.get(i));
        }

        System.out.println("after filtering: samples = " + rrMsFiltered.size());
        if (rrMsFiltered.size() < 2) {
            System.out.println("too few filtered samples for RMSSD/SDNN");
            return;
        }

        // SDNN
        double mean = rrMsFiltered.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double sumSq = rrMsFiltered.stream().mapToDouble(r -> Math.pow(r - mean, 2)).sum();
        double sdnn = Math.sqrt(sumSq / (rrMsFiltered.size() - 1));

        // RMSSD & pNN50
        double sumDiffSq = 0;
        int cnt50 = 0;
        for (int i = 0; i < rrMsFiltered.size() - 1; i++) {
            double diff = rrMsFiltered.get(i + 1) - rrMsFiltered.get(i);
            sumDiffSq += diff * diff;
            if (Math.abs(diff) > 50) cnt50++;
        }
        double rmssd = Math.sqrt(sumDiffSq / Math.max(1, rrMsFiltered.size() - 1));
        double pnn50 = (rrMsFiltered.size() > 1) ? (cnt50 / (double)(rrMsFiltered.size() - 1) * 100.0) : 0.0;

        System.out.printf("Filtered SDNN(ms)=%.3f RMSSD(ms)=%.3f pNN50=%.2f%%%n", sdnn, rmssd, pnn50);
        System.out.printf("RR mean(ms)=%.3f RR std(ms)=%.3f%n", mean, Math.sqrt(sumSq / (rrMsFiltered.size() - 1)));
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
