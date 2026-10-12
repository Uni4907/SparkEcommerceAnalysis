package com.uni.platform.adapter;

import com.uni.platform.model.PlatformModels.DatasetRecord;
import com.uni.platform.model.PlatformModels.FieldMapping;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.lower;
import static org.apache.spark.sql.functions.trim;
import static org.apache.spark.sql.functions.unix_timestamp;
import static org.apache.spark.sql.functions.when;

/** Maps a confirmed arbitrary CSV schema into one or both standard DWD models. */
public class GenericCSVAdapter implements DatasetAdapter {
    @Override public boolean supports(DatasetRecord dataset) { return true; }

    @Override public StandardDataset adapt(SparkSession spark, DatasetRecord dataset, List<FieldMapping> mappings) {
        Dataset<Row> raw = spark.read()
                .option("header", Boolean.toString(dataset.hasHeader()))
                .option("delimiter", dataset.delimiter())
                .option("inferSchema", "false")
                .option("mode", "PERMISSIVE")
                .csv(dataset.filePath());

        Map<String, Column> standard = new LinkedHashMap<>();
        mappings.stream().filter(FieldMapping::confirmed).forEach(mapping ->
                standard.put(mapping.standardField(), col(mapping.sourceField())));

        Dataset<Row> behavior = null;
        if (standard.containsKey("event_type")) {
            behavior = raw.select(
                            lit(dataset.id()).as("dataset_id"),
                            stringValue(standard.get("user_id")).as("user_id"),
                            stringValue(standard.get("item_id")).as("item_id"),
                            stringValue(standard.get("category_id")).as("category_id"),
                            lower(trim(standard.get("event_type"))).as("event_type"),
                            epochSeconds(standard.get("event_time")).as("event_time"))
                    .filter(col("event_type").isNotNull());
        }

        Dataset<Row> orders = null;
        if (standard.containsKey("order_id") || standard.containsKey("amount")) {
            Column quantity = numberValue(standard.get("quantity"), 1.0);
            Column unitPrice = numberValue(standard.get("unit_price"), 0.0);
            Column amount = standard.containsKey("amount")
                    ? standard.get("amount").cast("double") : quantity.multiply(unitPrice);
            orders = raw.select(
                            lit(dataset.id()).as("dataset_id"),
                            stringValue(standard.get("order_id")).as("order_id"),
                            stringValue(standard.get("user_id")).as("user_id"),
                            stringValue(standard.get("item_id")).as("item_id"),
                            stringValue(standard.get("category_id")).as("category_id"),
                            epochSeconds(standard.get("order_time")).as("order_time"),
                            quantity.as("quantity"), unitPrice.as("unit_price"), amount.as("amount"))
                    .filter(col("user_id").isNotNull());
        }
        if (behavior == null && orders == null) {
            throw new IllegalArgumentException("映射无法构成行为或订单标准模型");
        }
        return new StandardDataset(dataset.id(), behavior, orders);
    }

    private static Column stringValue(Column value) {
        return value == null ? lit(null).cast("string") : trim(value.cast("string"));
    }

    private static Column numberValue(Column value, double fallback) {
        return value == null ? lit(fallback) : coalesce(value.cast("double"), lit(fallback));
    }

    private static Column epochSeconds(Column value) {
        if (value == null) return lit(null).cast("long");
        Column numeric = value.cast("long");
        Column normalized = when(numeric.gt(9_999_999_999L), numeric.divide(1000).cast("long"))
                .otherwise(numeric);
        return coalesce(normalized, unix_timestamp(value));
    }
}
