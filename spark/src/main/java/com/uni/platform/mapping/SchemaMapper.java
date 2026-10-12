package com.uni.platform.mapping;

import com.uni.platform.model.PlatformModels.FieldMapping;
import com.uni.platform.model.PlatformModels.FieldProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic alias + type + sample recommendation. No external AI is used. */
public class SchemaMapper {
    public static final Set<String> STANDARD_FIELDS = Set.of(
            "user_id", "item_id", "category_id", "event_type", "event_time",
            "order_id", "order_time", "quantity", "unit_price", "amount");

    private static final Map<String, List<String>> ALIASES = aliases();

    public List<FieldMapping> recommend(long datasetId, String datasetType, List<FieldProfile> profiles) {
        List<FieldMapping> result = new ArrayList<>();
        for (FieldProfile profile : profiles) {
            Recommendation recommendation = recommendOne(datasetType, profile);
            result.add(new FieldMapping(datasetId, profile.sourceField(), profile.sourceIndex(),
                    recommendation.field, profile.inferredType(), recommendation.confidence,
                    false, profile.samples()));
        }
        return List.copyOf(result);
    }

    private Recommendation recommendOne(String datasetType, FieldProfile profile) {
        if ("TAOBAO_BEHAVIOR".equalsIgnoreCase(datasetType) && profile.sourceField().startsWith("_c")) {
            String[] fields = {"user_id", "item_id", "category_id", "event_type", "event_time"};
            if (profile.sourceIndex() < fields.length) return new Recommendation(fields[profile.sourceIndex()], 1.0);
        }

        String normalized = normalize(profile.sourceField());
        for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
            if (entry.getValue().contains(normalized)) return new Recommendation(entry.getKey(), 0.98);
        }

        // Type/sample signals only provide a cautious recommendation.
        if (profile.inferredType().equals("timestamp")) return new Recommendation("event_time", 0.62);
        if (profile.samples().stream().allMatch(v -> Set.of("pv", "cart", "fav", "buy").contains(v.toLowerCase(Locale.ROOT)))) {
            return new Recommendation("event_type", 0.95);
        }
        return new Recommendation(null, 0.0);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static Map<String, List<String>> aliases() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("user_id", List.of("userid", "user", "buyerid", "customerid", "memberid"));
        map.put("item_id", List.of("itemid", "item", "productid", "product", "skuid", "goodsid"));
        map.put("category_id", List.of("categoryid", "category", "catid", "productcategory"));
        map.put("event_type", List.of("eventtype", "event", "behavior", "behaviour", "action", "actiontype"));
        map.put("event_time", List.of("eventtime", "actiontime", "timestamp", "ts", "createdat"));
        map.put("order_id", List.of("orderid", "orderno", "transactionid"));
        map.put("order_time", List.of("ordertime", "purchasetime", "paidat", "paymenttime"));
        map.put("quantity", List.of("quantity", "qty", "count", "units"));
        map.put("unit_price", List.of("unitprice", "price", "saleprice"));
        map.put("amount", List.of("amount", "totalamount", "revenue", "salesamount", "gmv"));
        return map;
    }

    private record Recommendation(String field, double confidence) {}
}
