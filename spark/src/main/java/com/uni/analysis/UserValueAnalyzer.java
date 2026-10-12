package com.uni.analysis;

import com.uni.AnalysisContext;
import com.uni.util.ResultExporter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.when;

/**
 * 阶段五：用户价值分析（A/F/R 评分模型）。
 *
 * 规则与当前代码保持一致（当前代码相对早期文档版本新增了"低价值用户"，
 * R_score 的阈值也以当前 Main.java 为准，详见 docs/模型调整说明.md）：
 *
 *  A（购买范围）：purchase_item_count + purchase_category_count
 *  F（购买频率）：purchase_count
 *  R（最近购买）：days_since_last_purchase
 *
 *  value_score = A_score + F_score + R_score
 *  等级：0 → 未购买用户；≤6 → 低价值用户；≤11 → 普通价值用户；其余 → 高价值用户
 */
public class UserValueAnalyzer {

    private final AnalysisContext ctx;
    private final UserProfileAnalyzer profile;

    /** 用户价值宽表（含 A/F/R 分数与价值等级） */
    public Dataset<Row> userValueProfile;

    public UserValueAnalyzer(AnalysisContext ctx, UserProfileAnalyzer profile) {
        this.ctx = ctx;
        this.profile = profile;
    }

    public void run() {
        System.out.println("========== 阶段五：用户价值分析（A/F/R） ==========");

        userValueProfile = profile.userProfile.select(
                        "user_id",
                        "purchase_count",
                        "purchase_item_count",
                        "purchase_category_count",
                        "days_since_last_purchase"
                )
                .withColumn("purchase_range",
                        col("purchase_item_count").plus(col("purchase_category_count")))
                .withColumn("A_score",
                        when(col("purchase_range").geq(20), 5)
                                .when(col("purchase_range").geq(10), 4)
                                .when(col("purchase_range").geq(5), 3)
                                .when(col("purchase_range").gt(0), 2)
                                .otherwise(0))
                .withColumn("F_score",
                        when(col("purchase_count").geq(20), 5)
                                .when(col("purchase_count").geq(10), 4)
                                .when(col("purchase_count").geq(5), 3)
                                .when(col("purchase_count").gt(0), 2)
                                .otherwise(0))
                .withColumn("R_score",
                        when(col("days_since_last_purchase").isNull(), 0)
                                .when(col("days_since_last_purchase").leq(3), 5)
                                .when(col("days_since_last_purchase").leq(7), 4)
                                .when(col("days_since_last_purchase").leq(30), 3)
                                .when(col("days_since_last_purchase").leq(90), 2)
                                .otherwise(1))
                .withColumn("value_score",
                        col("A_score").plus(col("F_score")).plus(col("R_score")))
                .withColumn("user_value_level",
                        when(col("value_score").equalTo(0), "未购买用户")
                                .when(col("value_score").leq(6), "低价值用户")
                                .when(col("value_score").leq(11), "普通价值用户")
                                .otherwise("高价值用户"));
        // 价值宽表约 98 万行，缓存后供分群与群体差异分析直接复用
        userValueProfile.cache();
        userValueProfile.count();
        userValueProfile.show(10);

        // 各维度分数分布与总分分布
        Dataset<Row> aCounts = userValueProfile.groupBy("A_score")
                .count().orderBy("A_score");
        Dataset<Row> fCounts = userValueProfile.groupBy("F_score")
                .count().orderBy("F_score");
        Dataset<Row> rCounts = userValueProfile.groupBy("R_score")
                .count().orderBy("R_score");
        Dataset<Row> valueScoreCounts = userValueProfile.groupBy("value_score")
                .count().orderBy("value_score");
        Dataset<Row> valueLevelCounts = userValueProfile.groupBy("user_value_level")
                .count().orderBy("user_value_level");

        aCounts.show(20);
        fCounts.show(20);
        rCounts.show(20);
        valueScoreCounts.show(30);
        valueLevelCounts.show(20);

        ResultExporter.exportTable(aCounts, ctx.config.outputDir, "a_score_counts");
        ResultExporter.exportTable(fCounts, ctx.config.outputDir, "f_score_counts");
        ResultExporter.exportTable(rCounts, ctx.config.outputDir, "r_score_counts");
        ResultExporter.exportTable(valueScoreCounts, ctx.config.outputDir, "value_score_counts");
        ResultExporter.exportTable(valueLevelCounts, ctx.config.outputDir, "value_level_counts");
    }
}
