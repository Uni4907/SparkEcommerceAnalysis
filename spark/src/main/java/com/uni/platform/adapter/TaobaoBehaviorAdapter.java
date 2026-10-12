package com.uni.platform.adapter;

import com.uni.platform.model.PlatformModels.DatasetRecord;
import com.uni.platform.model.PlatformModels.FieldMapping;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;

import java.util.List;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.lower;
import static org.apache.spark.sql.functions.trim;

/** Exact migration of the five-column Taobao source contract into dwd_behavior_event. */
public class TaobaoBehaviorAdapter implements DatasetAdapter {
    @Override public boolean supports(DatasetRecord dataset) {
        return "TAOBAO_BEHAVIOR".equalsIgnoreCase(dataset.datasetType());
    }

    @Override public StandardDataset adapt(SparkSession spark, DatasetRecord dataset, List<FieldMapping> mappings) {
        StructType schema = new StructType()
                .add("user_id", DataTypes.LongType, true)
                .add("item_id", DataTypes.LongType, true)
                .add("category_id", DataTypes.LongType, true)
                .add("event_type", DataTypes.StringType, true)
                .add("event_time", DataTypes.LongType, true);
        Dataset<Row> raw = spark.read().schema(schema)
                .option("header", "false")
                .option("delimiter", dataset.delimiter())
                .option("mode", "PERMISSIVE")
                .csv(dataset.filePath());

        Dataset<Row> behavior = raw
                .withColumn("dataset_id", lit(dataset.id()))
                .withColumn("user_id", col("user_id").cast("string"))
                .withColumn("item_id", col("item_id").cast("string"))
                .withColumn("category_id", col("category_id").cast("string"))
                .withColumn("event_type", lower(trim(col("event_type"))))
                .filter(col("user_id").isNotNull()
                        .and(col("item_id").isNotNull())
                        .and(col("event_type").isin("pv", "cart", "fav", "buy"))
                        .and(col("event_time").isNotNull()))
                .select("dataset_id", "user_id", "item_id", "category_id", "event_type", "event_time");
        return new StandardDataset(dataset.id(), behavior, null);
    }
}
