package com.uni.analysis;

import com.uni.AnalysisContext;
import com.uni.util.ResultExporter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.sum;
import static org.apache.spark.sql.functions.when;

/**
 * 阶段八：商品分析。
 *
 * 从商品与类别两个粒度统计热度与转化：
 *  - 商品浏览量 / 加购量 / 收藏量 / 购买量
 *  - 热门商品排行（按浏览、按购买）
 *  - 商品购买转化（购买事件 / 浏览事件，仅在有浏览的商品上计算）
 *  - 热门类别排行与类别转化
 *
 * 数据集中没有商品名称，因此只能使用商品/类别 ID 展示排行，
 * 报告中会明确说明这一限制。
 */
public class ProductAnalyzer {

    private final AnalysisContext ctx;

    public ProductAnalyzer(AnalysisContext ctx) {
        this.ctx = ctx;
    }

    public void run() {
        System.out.println("========== 阶段八：商品分析 ==========");

        int topN = ctx.config.topN;

        // 每个商品的四类行为统计
        Dataset<Row> itemBehaviorStats = ctx.df.groupBy("item_id")
                .agg(
                        sum(when(col("behavior").equalTo("pv"), 1).otherwise(0)).as("pv_count"),
                        sum(when(col("behavior").equalTo("cart"), 1).otherwise(0)).as("cart_count"),
                        sum(when(col("behavior").equalTo("fav"), 1).otherwise(0)).as("fav_count"),
                        sum(when(col("behavior").equalTo("buy"), 1).otherwise(0)).as("buy_count"),
                        count(lit(1)).as("total_actions")
                );

        // 每个商品的去重购买人数
        Dataset<Row> itemBuyerCount = ctx.buyData
                .select("item_id", "user_id")
                .distinct()
                .groupBy("item_id")
                .count()
                .withColumnRenamed("count", "buyer_count");

        Dataset<Row> itemStats = itemBehaviorStats
                .join(itemBuyerCount, "item_id", "left")
                .na().fill(0, new String[]{"buyer_count"});

        // 热门商品：按浏览 / 按购买（同时输出加购、收藏、购买人数）
        Dataset<Row> itemTopPv = itemStats.orderBy(col("pv_count").desc()).limit(topN);
        itemTopPv.show(20, false);
        Dataset<Row> itemTopBuy = itemStats
                .filter(col("pv_count").gt(0))
                .withColumn("conversion_rate",
                        col("buy_count").divide(col("pv_count")).multiply(100))
                .orderBy(col("buy_count").desc())
                .limit(topN);
        itemTopBuy.show(20, false);

        ResultExporter.exportTable(itemTopPv, ctx.config.outputDir, "item_top_pv");
        ResultExporter.exportTable(itemTopBuy, ctx.config.outputDir, "item_top_buy");

        // 类别行为统计与去重购买人数
        Dataset<Row> categoryBehaviorStats = ctx.df.groupBy("category_id")
                .agg(
                        sum(when(col("behavior").equalTo("pv"), 1).otherwise(0)).as("pv_count"),
                        sum(when(col("behavior").equalTo("cart"), 1).otherwise(0)).as("cart_count"),
                        sum(when(col("behavior").equalTo("fav"), 1).otherwise(0)).as("fav_count"),
                        sum(when(col("behavior").equalTo("buy"), 1).otherwise(0)).as("buy_count"),
                        count(lit(1)).as("total_actions")
                );

        Dataset<Row> categoryBuyerCount = ctx.buyData
                .select("category_id", "user_id")
                .distinct()
                .groupBy("category_id")
                .count()
                .withColumnRenamed("count", "buyer_count");

        Dataset<Row> categoryStats = categoryBehaviorStats
                .join(categoryBuyerCount, "category_id", "left")
                .na().fill(0, new String[]{"buyer_count"});

        Dataset<Row> categoryTopPv = categoryStats
                .orderBy(col("pv_count").desc()).limit(topN);
        categoryTopPv.show(20, false);

        Dataset<Row> categoryTopBuy = categoryStats
                .filter(col("pv_count").gt(0))
                .withColumn("conversion_rate",
                        col("buy_count").divide(col("pv_count")).multiply(100))
                .orderBy(col("buy_count").desc())
                .limit(topN);
        categoryTopBuy.show(20, false);

        ResultExporter.exportTable(categoryTopPv, ctx.config.outputDir, "category_top_pv");
        ResultExporter.exportTable(categoryTopBuy, ctx.config.outputDir, "category_top_buy");
    }
}
