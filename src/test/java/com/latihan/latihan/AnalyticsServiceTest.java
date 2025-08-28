package com.latihan.latihan;

import com.latihan.latihan.DTO.ExpressionPoint;
import com.latihan.latihan.DTO.HrPoint;
import com.latihan.latihan.DTO.TimelineEntry;
import com.latihan.latihan.DTO.VoicePoint;
import com.latihan.latihan.Service.AnalyticsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsServiceTest {
    private final AnalyticsService analytics = new AnalyticsService();

    private HrPoint hr(long time, Double bpm) {
        HrPoint h = new HrPoint();
        // setTime almost always exists as long/Long; guard if setter expects primitive
        try { h.setTime(time); } catch (Exception ignored) {}
        // Only call setBpm if bpm != null to avoid autounboxing null -> NPE
        if (bpm != null) {
            try { h.setBpm(bpm); } catch (Exception ignored) {}
        }
        return h;
    }

    private TimelineEntry te(Long time, HrPoint hr, VoicePoint v, ExpressionPoint e) {
        TimelineEntry t = new TimelineEntry();
        if (time != null) {
            try { t.setTime(time); } catch (Exception ignored) {}
        }
        if (hr != null) {
            try { t.setHr(hr); } catch (Exception ignored) {}
        }
        if (v != null) {
            try { t.setVoice(v); } catch (Exception ignored) {}
        }
        if (e != null) {
            try { t.setExpression(e); } catch (Exception ignored) {}
        }
        return t;
    }

    private VoicePoint vp(Long time, Boolean valid, Double pitch, Double volume) {
        VoicePoint v = new VoicePoint();
        if (time != null) {
            try { v.setTime(time); } catch (Exception ignored) {}
        }
        // Only call setValid if not null (avoid autounboxing)
        if (valid != null) {
            try { v.setValid(valid); } catch (Exception ignored) {}
        }
        if (pitch != null) {
            try { v.setPitch(pitch); } catch (Exception ignored) {}
        }
        if (volume != null) {
            try { v.setVolume(volume); } catch (Exception ignored) {}
        }
        return v;
    }

    private ExpressionPoint ep(Long time, String emo, Double conf) {
        ExpressionPoint e = new ExpressionPoint();
        if (time != null) {
            try { e.setTime(time); } catch (Exception ignored) {}
        }
        if (emo != null) {
            try { e.setEmotion(emo); } catch (Exception ignored) {}
        }
        if (conf != null) {
            try { e.setConfidence(conf); } catch (Exception ignored) {}
        }
        return e;
    }

    @Test
    void computeDiagnostics_handlesNullHrAndNonNullHrWithoutThrowing() {
        TimelineEntry e1 = te(1000L, null, vp(1000L, true, 220.0, 0.2), ep(1000L, "neutral", 0.9));
        HrPoint h2 = hr(2000L, 72.0);
        TimelineEntry e2 = te(3000L, h2, vp(3000L, true, 210.0, 0.25), ep(3000L, "sad", 0.8));

        List<TimelineEntry> timeline = List.of(e1, e2);

        Map<String, Object> out = analytics.computeDiagnosticsFromTimeline(timeline, "hello world");

        assertNotNull(out, "computeDiagnosticsFromTimeline must not return null");
        assertEquals(1, ((Number) out.get("beatsUsed")).intValue(), "beatsUsed should count only valid HR entries");
        assertEquals(72.0, ((Number) out.get("meanBpm")).doubleValue(), 1e-6);
        assertEquals(2.0, ((Number) out.get("duration_s")).doubleValue(), 1e-6);
    }

    @Test
    void computeDiagnostics_allEntriesWithNullHr_returnsZeroBpmAndNoException() {
        TimelineEntry e1 = te(1000L, null, vp(1000L, true, 120.0, 0.1), ep(1000L, "happy", 0.7));
        TimelineEntry e2 = te(2500L, null, vp(2500L, true, 110.0, 0.15), ep(2500L, "neutral", 0.6));
        List<TimelineEntry> timeline = List.of(e1, e2);

        Map<String, Object> out = analytics.computeDiagnosticsFromTimeline(timeline, "some transcript");

        assertNotNull(out);
        assertEquals(0, ((Number) out.get("beatsUsed")).intValue(), "No HR readings -> beatsUsed == 0");
        assertEquals(0.0, ((Number) out.get("meanBpm")).doubleValue(), 1e-9, "meanBpm should be 0.0 when no valid HR");
        assertTrue(((Number) out.get("audioValidFraction")).doubleValue() >= 0.0);
    }

    @Test
    void computeHrv_ignoresNullAndInvalidBpms_andReturnsMapWithKeys() {
        HrPoint r1 = hr(1000L, null);
        HrPoint r2 = hr(2000L, -5.0);
        HrPoint r3 = hr(3000L, 60.0);
        HrPoint r4 = hr(4000L, 75.0);

        List<HrPoint> readings = List.of(r1, r2, r3, r4);

        Map<String, Double> hrv = analytics.computeHrv(readings);

        assertNotNull(hrv);
        assertTrue(hrv.containsKey("SDNN"));
        assertTrue(hrv.containsKey("RMSSD"));
        assertTrue(hrv.containsKey("pNN50"));

        assertTrue(hrv.get("SDNN") >= 0.0);
        assertTrue(hrv.get("RMSSD") >= 0.0);
    }

    @Test
    void computeFrequencyHrv_shortOrSparse_readings_returnsInvalidFlag() {
        HrPoint r1 = hr(1000L, 60.0);
        HrPoint r2 = hr(2000L, 62.0);
        HrPoint r3 = hr(3000L, 61.0);

        Map<String, Double> res = analytics.computeFrequencyHrv(List.of(r1, r2, r3));
        assertNotNull(res);
        assertEquals(0.0, res.getOrDefault("valid", 0.0), "Frequency HRV must mark invalid for short input");
    }

    @Test
    void summarizeVoice_handlesNoValidPoints() {
        // Create voice points where 'valid' is false or omitted (null)
        VoicePoint v1 = vp(1000L, false, -1.0, 0.0);
        VoicePoint v2 = vp(2000L, null, null, null); // omitted fields
        Map<String, Double> out = analytics.summarizeVoice(List.of(v1, v2));
        assertNotNull(out);
        assertEquals(0.0, out.getOrDefault("avgVolume", 0.0), 1e-9);
        assertEquals(0.0, out.getOrDefault("avgPitch", 0.0), 1e-9);
    }
}
