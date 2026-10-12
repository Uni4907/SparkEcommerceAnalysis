package com.uni.analysis;

import com.uni.AnalysisContext;
import com.uni.util.ResultExporter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.avg;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.when;

/**
 * 阶段三：用户行为与购买力分析。
 *
 * 内容与原 Main.java 一致：
 *  - 每个用户行为次数、平均行为次数
 *  - 活跃度分层：低活跃 / 普通 / 高活跃
 *  - 每个用户购买次数、全体用户平均购买次数
 *  - 购买频率分层：暂无购买 / 一次购买 / 普通购买 / 高频购买
 *  - 购买次数分布
 *
 * 本类还会把后续模块需要复用的中间结果保存为公开字段。
 */
public class UserActivityAnalyzer {

    private final AnalysisContext ctx;

    /** 每个用户的行为总数（按行为次数降序） */
    public Dataset<Row> userActionCount;

    /** 带活跃度等级的每用户数据 */
    public Dataset<Row> userActiveLevel;

    /** 每个购买用户的行为购买次数 */
    public Dataset<Row> userPurchaseCount;

    /** 全体用户（含未购买用户，purchase_count 可能为 NULL）的购买次数 */
    public Dataset<Row> allUserPurchaseCount;

    /** 带购买频率等级的每用户数据 */
    public Dataset<Row> userPurchaseSegment;

    public UserActivityAnalyzer(AnalysisContext ctx) {
        this.ctx = ctx;
    }

    public void run() {
        System.out.println("========== 阶段三：用户行为与购买力分析 ==========");

        Dataset<Row> df = ctx.df;
        Dataset<Row> buyData = ctx.buyData;

        // 每个用户的行为次数
        userActionCount = df.groupBy("user_id")
                .count()
                .withColumnRenamed("count", "action_count")
                .orderBy(col("action_count").desc());
        // 结果只有约 98 万行，缓存后供画像/分群等后续模块复用，
        // 避免每个阶段都重新执行一次对全量数据的 groupBy
        userActionCount.cache();
        userActionCount.count();
        userActionCount.show(10);

        // 平均行为次数
        double avgActionCount = userActionCount
                .select(avg("action_count"))
                .first().getDouble(0);
        System.out.println("平均行为次数：" + String.format("%.4f", avgActionCount));

        // 活跃度分层
        userActiveLevel = userActionCount
                .withColumn("user_active_level",
                        when(col("action_count").lt(50), "低活跃用户")
                                .when(col("action_count").lt(200), "普通用户")
                                .otherwise("高活跃用户"));
        userActiveLevel.show(10);

        Dataset<Row> activeLevelCounts = userActiveLevel.groupBy("user_active_level")
                .count()
                .withColumnRenamed("count", "user_count")
                .orderBy(col("user_active_level"));
        activeLevelCounts.show(20);

        // 每个购买用户的购买次数
        userPurchaseCount = buyData.groupBy("user_id")
                .count()
                .withColumnRenamed("count", "purchase_count")
                .orderBy(col("purchase_count").desc());
        userPurchaseCount.cache();
        userPurchaseCount.count();
        userPurchaseCount.show(10);

        // 保留没有购买行为的用户（左连接后 purchase_count 为 NULL）
        allUserPurchaseCount = df.select("user_id").distinct()
                .join(userPurchaseCount, "user_id", "left");
        allUserPurchaseCount.cache();
        allUserPurchaseCount.count();

        double avgPurchaseCount = allUserPurchaseCount
                .select(avg("purchase_count"))
                .first().getDouble(0);
        System.out.println("全体用户平均购买次数：" + String.format("%.5f", avgPurchaseCount));

        // 购买频率分层（规则与原代码一致）
        userPurchaseSegment = allUserPurchaseCount
                .withColumn("user_buy_level",
                        when(col("purchase_count").isNull(), "暂无购买用户")
                                .when(col("purchase_count").leq(1), "一次购买用户")
                                .when(col("purchase_count").leq(5), "普通购买用户")
                                .otherwise("高频购买用户"));

        Dataset<Row> buyLevelCounts = userPurchaseSegment.groupBy("user_buy_level")
                .count()
                .withColumnRenamed("count", "user_count")
                .orderBy(col("user_count").desc());
        buyLevelCounts.show(20);

        // 购买次数分布：将 NULL 视为 0，再按次数分组
        Dataset<Row> purchaseFrequency = allUserPurchaseCount
                .withColumn("purchase_count",
                        when(col("purchase_count").isNull(), 0)
                                .otherwise(col("purchase_count")))
                .groupBy("purchase_count")
                .count()
                .withColumnRenamed("count", "user_count")
                .orderBy(col("purchase_count"));
        purchaseFrequency.show(20);

        // 导出
        ResultExporter.exportTable(activeLevelCounts, ctx.config.outputDir, "activity_level_counts");
        ResultExporter.exportTable(buyLevelCounts, ctx.config.outputDir, "buy_level_counts");
        ResultExporter.exportTable(purchaseFrequency, ctx.config.outputDir, "purchase_frequency");
    }
}
