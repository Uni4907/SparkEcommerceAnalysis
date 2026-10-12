package com.uni.analysis;

import com.uni.AnalysisContext;
import com.uni.util.ResultExporter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.List;

import static org.apache.spark.sql.functions.col;

/**
 * 阶段一 + 阶段二：数据读取与基础统计。
 *
 * 内容与原 Main.java 完全一致：
 *  - 数据总量 / 用户数 / 商品数 / 商品类别数
 *  - 四种行为（pv/cart/fav/buy）数量与占比
 *  - 浏览→购买、加购→购买、收藏→购买的简单事件比例
 *  - 购买用户数、用户购买率
 *
 * 所有"已有结果"的定义保持不变，只是额外导出 CSV/JSON 供可视化使用。
 */
public class DataQualityAnalyzer {

    private final AnalysisContext ctx;

    public DataQualityAnalyzer(AnalysisContext ctx) {
        this.ctx = ctx;
    }

    public void run() {
        System.out.println("========== 阶段一/二：数据读取与总体统计 ==========");

        Dataset<Row> df = ctx.df;
        Dataset<Row> buyData = ctx.buyData;

        // 与原始代码一致：先查看前 10 条数据和结构
        df.show(10);
        df.printSchema();

        // 数据总量
        long totalBehaviorCount = df.count();
        ctx.totalBehaviorCount = totalBehaviorCount;
        System.out.println("数据总量：" + totalBehaviorCount);

        // 用户数量
        long totalUserCount = df.select("user_id").distinct().count();
        ctx.totalUserCount = totalUserCount;
        System.out.println("用户数量：" + totalUserCount);

        // 商品数量
        long totalItemCount = df.select("item_id").distinct().count();
        System.out.println("商品数量：" + totalItemCount);

        // 商品类别数量
        long totalCategoryCount = df.select("category_id").distinct().count();
        System.out.println("商品类别数量：" + totalCategoryCount);

        // 各行为数量
        Dataset<Row> behaviorCounts = df.groupBy("behavior")
                .count()
                .orderBy(col("behavior"));
        behaviorCounts.show(20);

        // 各行为占比（事件次数占比）
        Dataset<Row> behaviorPercent = df.groupBy("behavior")
                .count()
                .withColumn("percentage",
                        col("count").divide(totalBehaviorCount).multiply(100))
                .orderBy(col("behavior"));
        behaviorPercent.show(20);

        // 行为次数统计（供后续模块复用）
        long pvCount = df.filter(col("behavior").equalTo("pv")).count();
        long buyCount = df.filter(col("behavior").equalTo("buy")).count();
        long cartCount = df.filter(col("behavior").equalTo("cart")).count();
        long favCount = df.filter(col("behavior").equalTo("fav")).count();

        // 简单事件次数转化率（与原代码口径一致）
        double pvBuyRate = (double) buyCount / pvCount * 100;
        double cartBuyRate = (double) buyCount / cartCount * 100;
        double favBuyRate = (double) buyCount / favCount * 100;

        System.out.println("浏览次数：" + pvCount);
        System.out.println("购买次数：" + buyCount);
        System.out.println("浏览购买转化率：" + String.format("%.4f", pvBuyRate) + "%");
        System.out.println("加购次数：" + cartCount);
        System.out.println("加购购买转化率：" + String.format("%.4f", cartBuyRate) + "%");
        System.out.println("收藏次数：" + favCount);
        System.out.println("收藏购买转化率：" + String.format("%.4f", favBuyRate) + "%");

        // 购买用户数与用户购买率
        long buyUserCount = buyData.select("user_id").distinct().count();
        double userPurchaseRate = (double) buyUserCount / totalUserCount * 100;
        System.out.println("购买用户数：" + buyUserCount);
        System.out.println("用户购买率：" + String.format("%.4f", userPurchaseRate) + "%");

        // 导出：概要统计（手动拼装）
        String[] summaryCols = {"metric", "value"};
        List<Object[]> summary = new ArrayList<>();
        summary.add(new Object[]{"数据总量", totalBehaviorCount});
        summary.add(new Object[]{"用户数量", totalUserCount});
        summary.add(new Object[]{"商品数量", totalItemCount});
        summary.add(new Object[]{"商品类别数量", totalCategoryCount});
        summary.add(new Object[]{"购买用户数", buyUserCount});
        summary.add(new Object[]{"用户购买率(%)", round4(userPurchaseRate)});
        ResultExporter.manualTable(ctx.config.outputDir, "overall_stats", summaryCols, summary);

        // 导出：行为数量 / 占比 / 转化率
        ResultExporter.exportTable(behaviorCounts, ctx.config.outputDir, "behavior_counts");
        ResultExporter.exportTable(behaviorPercent, ctx.config.outputDir, "behavior_percent");

        List<Object[]> conversionRows = new ArrayList<>();
        conversionRows.add(new Object[]{"浏览→购买(事件数比)", pvCount, buyCount, round4(pvBuyRate)});
        conversionRows.add(new Object[]{"加购→购买(事件数比)", cartCount, buyCount, round4(cartBuyRate)});
        conversionRows.add(new Object[]{"收藏→购买(事件数比)", favCount, buyCount, round4(favBuyRate)});
        conversionRows.add(new Object[]{"用户购买率(人数比)", totalUserCount, buyUserCount, round4(userPurchaseRate)});
        ResultExporter.manualTable(ctx.config.outputDir, "conversion_rates",
                new String[]{"conversion", "basis_count", "target_count", "rate_percent"},
                conversionRows);
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
