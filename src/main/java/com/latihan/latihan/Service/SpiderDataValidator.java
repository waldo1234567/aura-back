package com.latihan.latihan.Service;
import java.util.regex.*;
import java.util.*;
public class SpiderDataValidator {
    private static final Pattern SPIDER_PATTERN =
            Pattern.compile("SPIDER_DATA:([0-9]{1,3}(?:,[0-9]{1,3}){5}),CONF:([0-9]{1,3})", Pattern.MULTILINE);

    public static Map<String,Object> parseAndValidateSpiderData(
            String aiReply,
            Map<String,Object> metrics,
            AnalyticsService analyticsService
    ){
        Map<String,Object> result = new HashMap<>();

        try {
            // 1) deterministic fallback
            @SuppressWarnings("unchecked")
            Map<String,Object> fallbackMap = analyticsService.computeSpiderScores(
                    (Map<String,Object>) metrics.get("face"),
                    (Map<String,Double>) metrics.get("hrvTime"),
                    (Map<String,Double>) metrics.get("hrvFreq"),
                    (Map<String,Double>) metrics.get("voiceTime"),
                    (Map<String,Object>) metrics.get("voiceAdv"),
                    (Map<String,Object>) metrics.get("diagnostics")
            );

            // extract fallback axis ints
            List<String> axes = List.of("Stress","LowMood","SocialWithdrawal","Irritability","CognitiveFatigue","Arousal");
            Map<String,Integer> fallbackScores = new LinkedHashMap<>();
            for (String a : axes) {
                fallbackScores.put(a, clampInt(toIntSafe(fallbackMap.get(a)), 0, 100));
            }
            int fallbackConf = clampInt(toIntSafe(fallbackMap.get("confidence")), 0, 100);

            // 2) find last SPIDER_DATA line (if any)
            Matcher m = SPIDER_PATTERN.matcher(aiReply);
            String lastMatch = null;
            String lastGroup1 = null;
            String lastGroup2 = null;
            while (m.find()) {
                lastMatch = m.group(0);
                lastGroup1 = m.group(1);
                lastGroup2 = m.group(2);
            }

            if (lastMatch == null) {
                // no model line -> fallback
                result.put("scores", fallbackScores);
                result.put("confidence", fallbackConf);
                result.put("source", "fallback_no_model_line");
                result.put("rawLine", "");
                result.put("modelDiff", Double.POSITIVE_INFINITY);
                return result;
            }

            // 3) parse model values
            String[] nums = lastGroup1.split(",");
            if (nums.length != 6) throw new IllegalArgumentException("bad count");
            Map<String,Integer> modelScores = new LinkedHashMap<>();
            for (int i = 0; i < nums.length; i++) {
                modelScores.put(axes.get(i), clampInt(Integer.parseInt(nums[i].trim()), 0, 100));
            }
            int modelConf = clampInt(Integer.parseInt(lastGroup2.trim()), 0, 100);

            // 4) basic checks: confidence threshold and non-zero validity
            if (modelConf < 30) {
                // too low model confidence -> fallback
                result.put("scores", fallbackScores);
                result.put("confidence", fallbackConf);
                result.put("source", "fallback_low_model_conf");
                result.put("rawLine", lastMatch);
                result.put("modelDiff", Double.POSITIVE_INFINITY);
                return result;
            }

            // 5) compare with deterministic fallback (average absolute difference)
            double sumAbs = 0.0;
            for (String a : axes) {
                sumAbs += Math.abs(modelScores.get(a) - fallbackScores.get(a));
            }
            double avgAbsDiff = sumAbs / axes.size();

            // threshold: if model deviates substantially from deterministic result, use fallback
            final double DIFF_THRESHOLD = 15.0; // tuneable
            if (avgAbsDiff > DIFF_THRESHOLD) {
                result.put("scores", fallbackScores);
                result.put("confidence", fallbackConf);
                result.put("source", "fallback_model_disagrees");
                result.put("rawLine", lastMatch);
                result.put("modelDiff", avgAbsDiff);
                return result;
            }

            // 6) passed checks: accept model output
            result.put("scores", modelScores);
            result.put("confidence", modelConf);
            result.put("source", "model");
            result.put("rawLine", lastMatch);
            result.put("modelDiff", avgAbsDiff);
            return result;

        } catch (Exception ex) {
            // any parsing/exception -> fallback and log
            ex.printStackTrace();
            result.put("scores", Collections.emptyMap());
            result.put("confidence", 0);
            result.put("source", "fallback_exception");
            result.put("rawLine", "");
            result.put("modelDiff", Double.POSITIVE_INFINITY);
            return result;
        }
    }

    private static int toIntSafe(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number)o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

}
