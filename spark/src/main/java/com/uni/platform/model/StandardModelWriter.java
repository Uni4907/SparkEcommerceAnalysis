package com.uni.platform.model;

import com.uni.platform.adapter.StandardDataset;
import org.apache.spark.sql.SparkSession;

/** Optional Hive materialization, isolated and retry-safe by dataset_id partition. */
public class StandardModelWriter {
    public void appendToHive(StandardDataset data) {
        SparkSession spark = data.hasBehavior()
                ? data.behaviorEvents().sparkSession()
                : data.orders().sparkSession();
        ensureSchema(spark);
        spark.sql("SET hive.exec.dynamic.partition=true");
        spark.sql("SET hive.exec.dynamic.partition.mode=nonstrict");

        if (data.hasBehavior()) {
            data.behaviorEvents().select("user_id", "item_id", "category_id", "event_type", "event_time", "dataset_id")
                    .createOrReplaceTempView("v3_behavior_input");
            spark.sql("INSERT OVERWRITE TABLE ecommerce_v3.dwd_behavior_event PARTITION (dataset_id) "
                    + "SELECT user_id,item_id,category_id,event_type,event_time,dataset_id FROM v3_behavior_input");
        }
        if (data.hasOrders()) {
            data.orders().select("order_id", "user_id", "item_id", "category_id", "order_time",
                            "quantity", "unit_price", "amount", "dataset_id")
                    .createOrReplaceTempView("v3_order_input");
            spark.sql("INSERT OVERWRITE TABLE ecommerce_v3.dwd_order PARTITION (dataset_id) "
                    + "SELECT order_id,user_id,item_id,category_id,order_time,quantity,unit_price,amount,dataset_id "
                    + "FROM v3_order_input");
        }
    }

    private static void ensureSchema(SparkSession spark) {
        spark.sql("CREATE DATABASE IF NOT EXISTS ecommerce_v3");
        spark.sql("CREATE TABLE IF NOT EXISTS ecommerce_v3.dwd_behavior_event ("
                + "user_id STRING,item_id STRING,category_id STRING,event_type STRING,event_time BIGINT) "
                + "PARTITIONED BY (dataset_id BIGINT) STORED AS PARQUET");
        spark.sql("CREATE TABLE IF NOT EXISTS ecommerce_v3.dwd_order ("
                + "order_id STRING,user_id STRING,item_id STRING,category_id STRING,order_time BIGINT,"
                + "quantity DOUBLE,unit_price DOUBLE,amount DOUBLE) "
                + "PARTITIONED BY (dataset_id BIGINT) STORED AS PARQUET");
    }
}
