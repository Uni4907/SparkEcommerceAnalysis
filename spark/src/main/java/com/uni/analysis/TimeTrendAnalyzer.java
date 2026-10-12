package com.uni.analysis;

import com.uni.AnalysisContext;
import com.uni.util.ResultExporter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.List;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.from_unixtime;
import static org.apache.spark.sql.functions.hour;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.max;
import static org.apache.spark.sql.functions.min;
import static org.apache.spark.sql.functions.sum;
import static org.apache.spark.sql.functions.to_date;
import static org.apache.spark.sql.functions.when;

/**
 * 阶段十：时间维度趋势分析。
 *
 * 文档特别提醒：原始时间戳存在异常值（如 2122867355 对应 2037 年），
 * 不能直接假设整个数据的最大时间戳就是有效时间。
 *
 * 处理方式（不修改原始 df，不影响既有分析结果）：
 *  1. 先检查并打印原始时间戳 min/max 及异常统计；
 *  2. 趋势分析使用有效时间范围 [2017-11-25, 2017-12-03]
 *     （Kaggle UserBehavior 数据集公开的观测区间）内的数据；
 *  3. 输出日期趋势、小时趋势、购买时刻热力数据。
 */
public class TimeTrendAnalyzer {

    /** 数据集中公开的观测起始日期（闭区间） */
    private static final String VALID_START = "2017-11-25";
    /** 数据集中公开的观测结束日期（闭区间） */
    private static final String VALID_END = "2017-12-03";

    private final AnalysisContext ctx;

    public TimeTrendAnalyzer(AnalysisContext ctx) {
        this.ctx = ctx;
    }

    public void run() {
        System.out.println("========== 阶段十：时间维度趋势分析 ==========");

        // 1) 时间戳异常检查
        Row tsRange = ctx.df.select(min("timestamp").as("min_ts"), max("timestamp").as("max_ts"))
                .first();
        long rawMin = tsRange.getLong(0);
        long rawMax = tsRange.getLong(1);
        System.out.println("原始最小时间戳：" + rawMin);
        System.out.println("原始最大时间戳：" + rawMax);
        System.out.println("原始最大时间戳对应日期：" +
                ctx.spark.sql("select from_unixtime(" + rawMax + ") as d").first().getString(0));

        Dataset<Row> withDate = ctx.df.withColumn("action_date",
                to_date(from_unixtime(col("timestamp"))));

        // 统计有效区间外的记录数（异常记录不会进入任何时间趋势）
        long outsideCount = withDate
                .filter(col("action_date").lt(lit(VALID_START))
                        .or(col("action_date").gt(lit(VALID_END))))
                .count();
        long totalCount = ctx.totalBehaviorCount;
        System.out.println("有效区间外记录数：" + outsideCount
                + "（占总记录 " + String.format("%.6f", outsideCount * 100.0 / totalCount) + "%）");

        // 异常记录按日期分布（通常是极少数远离观测区间的脏数据）
        withDate.filter(col("action_date").lt(lit(VALID_START))
                        .or(col("action_date").gt(lit(VALID_END))))
                .groupBy("action_date")
                .count()
                .orderBy(col("action_date"))
                .show(30, false);

        Dataset<Row> validDf = withDate
                .filter(col("action_date").geq(lit(VALID_START))
                        .and(col("action_date").leq(lit(VALID_END))));

        // 2) 日期趋势：每天 pv/cart/fav/buy 与总行为
        Dataset<Row> dateTrend = validDf.groupBy("action_date")
                .agg(
                        sum(when(col("behavior").equalTo("pv"), 1).otherwise(0)).as("pv_count"),
                        sum(when(col("behavior").equalTo("cart"), 1).otherwise(0)).as("cart_count"),
                        sum(when(col("behavior").equalTo("fav"), 1).otherwise(0)).as("fav_count"),
                        sum(when(col("behavior").equalTo("buy"), 1).otherwise(0)).as("buy_count"),
                        count(lit(1)).as("total_actions")
                )
                .orderBy(col("action_date"));
        dateTrend.show(30, false);

        // 3) 小时趋势：一天 24 小时的行为强度（跨日期汇总）
        Dataset<Row> hourTrend = validDf
                .withColumn("action_hour", hour(from_unixtime(col("timestamp"))))
                .groupBy("action_hour")
                .agg(
                        sum(when(col("behavior").equalTo("pv"), 1).otherwise(0)).as("pv_count"),
                        sum(when(col("behavior").equalTo("cart"), 1).otherwise(0)).as("cart_count"),
                        sum(when(col("behavior").equalTo("fav"), 1).otherwise(0)).as("fav_count"),
                        sum(when(col("behavior").equalTo("buy"), 1).otherwise(0)).as("buy_count"),
                        count(lit(1)).as("total_actions")
                )
                .orderBy(col("action_hour"));
        hourTrend.show(30, false);

        // 4) 购买热力：(日期, 小时) 的购买次数，用于热力图
        Dataset<Row> dayHourBuy = validDf
                .filter(col("behavior").equalTo("buy"))
                .withColumn("action_hour", hour(from_unixtime(col("timestamp"))))
                .groupBy("action_date", "action_hour")
                .count()
                .withColumnRenamed("count", "buy_count")
                .orderBy(col("action_date"), col("action_hour"));
        dayHourBuy.show(30, false);

        // 导出
        ResultExporter.exportTable(dateTrend, ctx.config.outputDir, "date_trend");
        ResultExporter.exportTable(hourTrend, ctx.config.outputDir, "hour_trend");
        ResultExporter.exportTable(dayHourBuy, ctx.config.outputDir, "day_hour_buy");

        // 时间异常统计汇总
        String[] cols = {"metric", "value"};
        List<Object[]> summary = new ArrayList<>();
        summary.add(new Object[]{"原始最小时间戳", rawMin});
        summary.add(new Object[]{"原始最大时间戳", rawMax});
        summary.add(new Object[]{"有效时间范围", VALID_START + " ~ " + VALID_END});
        summary.add(new Object[]{"有效区间外记录数", outsideCount});
        summary.add(new Object[]{
                "有效区间外记录占比(%)",
                Math.round(outsideCount * 10000.0 / totalCount) / 100.0
        });
        ResultExporter.manualTable(ctx.config.outputDir, "time_range_stats", cols, summary);
    }
}
