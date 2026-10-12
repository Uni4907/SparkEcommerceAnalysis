package com.uni.platform.analysis;

import com.uni.platform.adapter.StandardDataset;
import com.uni.platform.result.VersionedResultWriter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.List;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.countDistinct;
import static org.apache.spark.sql.functions.from_unixtime;
import static org.apache.spark.sql.functions.hour;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.sum;
import static org.apache.spark.sql.functions.to_date;
import static org.apache.spark.sql.functions.when;

/** Dataset-agnostic analysis modules operating only on the standard models. */
public class GenericAnalysisEngine {
    public void run(StandardDataset data, List<String> modules, VersionedResultWriter out) {
        for (String module : modules) {
            switch (module) {
                case "overview" -> overview(data, out);
                case "daily_trend" -> dailyTrend(data, out);
                case "hourly" -> hourly(data, out);
                case "behavior" -> behavior(data, out);
                case "funnel" -> funnel(data, out);
                case "items" -> items(data, out);
                case "categories" -> categories(data, out);
                case "user_segmentation" -> segmentation(data, out);
                case "repurchase" -> repurchase(data, out);
                case "sales" -> sales(data, out);
                default -> throw new IllegalArgumentException("未实现分析模块: " + module);
            }
        }
    }

    private void overview(StandardDataset data, VersionedResultWriter out) {
        List<Object[]> rows = new ArrayList<>();
        if (data.hasBehavior()) {
            Dataset<Row> df = data.behaviorEvents();
            rows.add(new Object[]{"behavior_records", df.count()});
            rows.add(new Object[]{"users", df.select("user_id").distinct().count()});
            rows.add(new Object[]{"items", df.select("item_id").distinct().count()});
            rows.add(new Object[]{"buy_events", df.filter(col("event_type").equalTo("buy")).count()});
        }
        if (data.hasOrders()) {
            Dataset<Row> df = data.orders();
            rows.add(new Object[]{"order_rows", df.count()});
            rows.add(new Object[]{"orders", df.select("order_id").distinct().count()});
            rows.add(new Object[]{"users", df.select("user_id").distinct().count()});
            double revenue = number(df.agg(sum("amount")).first().get(0));
            rows.add(new Object[]{"revenue", revenue});
        }
        out.manual("overview", new String[]{"metric", "value"}, rows);
    }

    private void behavior(StandardDataset data, VersionedResultWriter out) {
        requireBehavior(data, "behavior");
        out.table("behavior", data.behaviorEvents().groupBy("event_type").count().orderBy("event_type"));
    }

    private void dailyTrend(StandardDataset data, VersionedResultWriter out) {
        if (data.hasBehavior()) {
            Dataset<Row> result = data.behaviorEvents()
                    .withColumn("day", to_date(from_unixtime(col("event_time"))))
                    .groupBy("day").agg(count(lit(1)).as("event_count"),
                            sum(when(col("event_type").equalTo("buy"), 1).otherwise(0)).as("buy_count"))
                    .orderBy("day");
            out.table("daily_trend", result);
        } else {
            Dataset<Row> result = data.orders()
                    .withColumn("day", to_date(from_unixtime(col("order_time"))))
                    .groupBy("day").agg(countDistinct("order_id").as("order_count"),
                            sum("quantity").as("quantity"), sum("amount").as("revenue")).orderBy("day");
            out.table("daily_trend", result);
        }
    }

    private void hourly(StandardDataset data, VersionedResultWriter out) {
        if (data.hasBehavior()) {
            out.table("hourly", data.behaviorEvents()
                    .withColumn("hour", hour(from_unixtime(col("event_time"))))
                    .groupBy("hour").count().orderBy("hour"));
        } else {
            out.table("hourly", data.orders()
                    .withColumn("hour", hour(from_unixtime(col("order_time"))))
                    .groupBy("hour").agg(countDistinct("order_id").as("order_count"), sum("amount").as("revenue"))
                    .orderBy("hour"));
        }
    }

    private void funnel(StandardDataset data, VersionedResultWriter out) {
        requireBehavior(data, "funnel");
        Dataset<Row> stages = data.behaviorEvents().groupBy("event_type")
                .agg(count(lit(1)).as("event_count"), countDistinct("user_id").as("user_count"))
                .withColumn("stage_order", when(col("event_type").equalTo("pv"), 1)
                        .when(col("event_type").equalTo("cart"), 2)
                        .when(col("event_type").equalTo("fav"), 3)
                        .when(col("event_type").equalTo("buy"), 4).otherwise(99))
                .orderBy("stage_order").drop("stage_order");
        out.table("funnel", stages);
    }

    private void items(StandardDataset data, VersionedResultWriter out) {
        Dataset<Row> result;
        if (data.hasBehavior()) {
            result = data.behaviorEvents().filter(col("item_id").isNotNull()).groupBy("item_id")
                    .agg(count(lit(1)).as("actions"),
                            sum(when(col("event_type").equalTo("buy"), 1).otherwise(0)).as("buys"))
                    .orderBy(col("buys").desc(), col("actions").desc()).limit(20);
        } else {
            result = data.orders().filter(col("item_id").isNotNull()).groupBy("item_id")
                    .agg(sum("quantity").as("quantity"), sum("amount").as("revenue"))
                    .orderBy(col("revenue").desc()).limit(20);
        }
        out.table("items", result);
    }

    private void categories(StandardDataset data, VersionedResultWriter out) {
        Dataset<Row> result;
        if (data.hasBehavior()) {
            result = data.behaviorEvents().filter(col("category_id").isNotNull()).groupBy("category_id")
                    .agg(count(lit(1)).as("actions"),
                            sum(when(col("event_type").equalTo("buy"), 1).otherwise(0)).as("buys"))
                    .orderBy(col("buys").desc(), col("actions").desc()).limit(20);
        } else {
            result = data.orders().filter(col("category_id").isNotNull()).groupBy("category_id")
                    .agg(sum("quantity").as("quantity"), sum("amount").as("revenue"))
                    .orderBy(col("revenue").desc()).limit(20);
        }
        out.table("categories", result);
    }

    private void segmentation(StandardDataset data, VersionedResultWriter out) {
        Dataset<Row> users;
        if (data.hasBehavior()) {
            users = data.behaviorEvents().groupBy("user_id")
                    .agg(count(lit(1)).as("actions"),
                            sum(when(col("event_type").equalTo("buy"), 1).otherwise(0)).as("purchases"))
                    .withColumn("segment", when(col("purchases").geq(2), "repeat_buyer")
                            .when(col("purchases").equalTo(1), "one_time_buyer")
                            .when(col("actions").geq(5), "active_non_buyer").otherwise("light_non_buyer"));
        } else {
            users = data.orders().groupBy("user_id").agg(countDistinct("order_id").as("orders"))
                    .withColumn("segment", when(col("orders").geq(2), "repeat_buyer").otherwise("one_time_buyer"));
        }
        out.table("user_segmentation", users.groupBy("segment").count().orderBy(col("count").desc()));
    }

    private void repurchase(StandardDataset data, VersionedResultWriter out) {
        Dataset<Row> counts = data.hasBehavior()
                ? data.behaviorEvents().filter(col("event_type").equalTo("buy")).groupBy("user_id").count()
                : data.orders().groupBy("user_id").agg(countDistinct("order_id").as("count"));
        long buyers = counts.count();
        long repeat = counts.filter(col("count").geq(2)).count();
        double rate = buyers == 0 ? 0.0 : Math.round(repeat * 10000.0 / buyers) / 100.0;
        out.manual("repurchase", new String[]{"buyers", "repeat_buyers", "repeat_rate_percent"},
                List.<Object[]>of(new Object[]{buyers, repeat, rate}));
    }

    private void sales(StandardDataset data, VersionedResultWriter out) {
        if (!data.hasOrders()) throw new IllegalArgumentException("sales 需要订单标准模型");
        Dataset<Row> result = data.orders().agg(countDistinct("order_id").as("orders"),
                countDistinct("user_id").as("buyers"), sum("quantity").as("quantity"), sum("amount").as("revenue"));
        out.table("sales", result);
    }

    private static void requireBehavior(StandardDataset data, String module) {
        if (!data.hasBehavior()) throw new IllegalArgumentException(module + " 需要行为标准模型");
    }

    private static double number(Object value) { return value == null ? 0.0 : ((Number) value).doubleValue(); }
}
