package com.uni.platform.analysis;

import com.uni.platform.model.PlatformModels.CapabilityReport;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

public class AnalysisRegistry {
    private static final Map<String, String> MODULE_CAPABILITY = modules();

    public Set<String> available(CapabilityReport report) {
        Set<String> result = new LinkedHashSet<>();
        MODULE_CAPABILITY.forEach((module, capability) -> {
            if (report.enabled().contains(capability)) result.add(module);
        });
        return Set.copyOf(result);
    }

    public List<String> validateRequested(List<String> requested, CapabilityReport report) {
        Set<String> available = available(report);
        List<String> selected = requested == null || requested.isEmpty()
                ? MODULE_CAPABILITY.keySet().stream().filter(available::contains).toList()
                : requested;
        for (String module : selected) {
            if (!MODULE_CAPABILITY.containsKey(module)) throw new IllegalArgumentException("未知分析模块: " + module);
            if (!available.contains(module)) throw new IllegalArgumentException("数据集不具备模块能力: " + module);
        }
        return List.copyOf(selected);
    }

    private static Map<String, String> modules() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("overview", "overview");
        map.put("daily_trend", "time_analysis");
        map.put("hourly", "time_analysis");
        map.put("behavior", "behavior_analysis");
        map.put("funnel", "funnel_analysis");
        map.put("items", "item_analysis");
        map.put("categories", "category_analysis");
        map.put("user_segmentation", "segmentation_analysis");
        map.put("repurchase", "repurchase_analysis");
        map.put("sales", "sales_analysis");
        return Collections.unmodifiableMap(map);
    }
}
