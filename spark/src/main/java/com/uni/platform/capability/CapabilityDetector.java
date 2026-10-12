package com.uni.platform.capability;

import com.uni.platform.model.PlatformModels.CapabilityReport;
import com.uni.platform.model.PlatformModels.FieldMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CapabilityDetector {
    private static final Map<String, List<Set<String>>> RULES = rules();

    public CapabilityReport detect(long datasetId, List<FieldMapping> mappings) {
        Set<String> fields = new LinkedHashSet<>();
        mappings.stream().filter(FieldMapping::confirmed)
                .map(FieldMapping::standardField).filter(v -> v != null && !v.isBlank())
                .forEach(fields::add);

        Set<String> enabled = new LinkedHashSet<>();
        Map<String, List<String>> disabled = new LinkedHashMap<>();
        for (Map.Entry<String, List<Set<String>>> rule : RULES.entrySet()) {
            Set<String> matching = rule.getValue().stream().filter(fields::containsAll).findFirst().orElse(null);
            if (matching != null) enabled.add(rule.getKey());
            else {
                Set<String> best = rule.getValue().stream()
                        .min((a, b) -> Integer.compare(missing(a, fields), missing(b, fields))).orElse(Set.of());
                List<String> missing = best.stream().filter(v -> !fields.contains(v)).sorted().toList();
                disabled.put(rule.getKey(), missing);
            }
        }
        return new CapabilityReport(datasetId, Set.copyOf(enabled), Map.copyOf(disabled));
    }

    private static int missing(Set<String> required, Set<String> actual) {
        return (int) required.stream().filter(v -> !actual.contains(v)).count();
    }

    private static Map<String, List<Set<String>>> rules() {
        Map<String, List<Set<String>>> map = new LinkedHashMap<>();
        map.put("overview", List.of(Set.of("user_id"), Set.of("order_id"), Set.of("item_id")));
        map.put("behavior_analysis", List.of(Set.of("event_type", "user_id")));
        map.put("item_analysis", List.of(Set.of("item_id")));
        map.put("category_analysis", List.of(Set.of("category_id")));
        map.put("time_analysis", List.of(Set.of("event_time"), Set.of("order_time")));
        map.put("funnel_analysis", List.of(Set.of("user_id", "item_id", "event_type", "event_time")));
        map.put("repurchase_analysis", List.of(Set.of("user_id", "event_type"), Set.of("user_id", "order_id")));
        map.put("order_analysis", List.of(Set.of("order_id", "user_id")));
        map.put("sales_analysis", List.of(Set.of("order_id", "quantity"), Set.of("order_id", "amount")));
        map.put("revenue_analysis", List.of(Set.of("amount"), Set.of("quantity", "unit_price")));
        map.put("segmentation_analysis", List.of(Set.of("user_id", "event_type"), Set.of("user_id", "order_id")));
        return map;
    }
}
