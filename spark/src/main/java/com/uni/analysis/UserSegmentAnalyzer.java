package com.uni.analysis;

import com.uni.AnalysisContext;
import com.uni.util.ResultExporter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.when;

/**
 * 阶段六：用户分群（在既有用户价值模型之上新增，不覆盖原模型）。
 *
 * 分群规则（按优先级从高到低，保证每个用户只属于一个群体）：
 *  1. 未购买但有加购/收藏  → 潜在购买用户
 *  2. value_score = 0      → 未购买用户
 *  3. 高价值用户            → 高价值用户
 *  4. 购买次数 > 5          → 高频购买用户
 *  5. 行为次数 >= 200       → 高活跃低购买用户
 *  6. 购买次数 = 1          → 一次购买用户
 *  7. 其余                  → 普通购买用户
 *
 * 说明："潜在购买用户"是从原"未购买用户"中细分出来的，
 * 并未修改既有的 user_value_level（原模型保留不变）。
 */
public class UserSegmentAnalyzer {

    private final AnalysisContext ctx;
    private final UserProfileAnalyzer profile;
    private final UserValueAnalyzer value;

    /** 用户分群宽表 */
    public Dataset<Row> userSegment;

    public UserSegmentAnalyzer(AnalysisContext ctx,
                               UserProfileAnalyzer profile,
                               UserValueAnalyzer value) {
        this.ctx = ctx;
        this.profile = profile;
        this.value = value;
    }

    public void run() {
        System.out.println("========== 阶段六：用户分群 ==========");

        // 画像信息(含各行为次数与活跃等级) + 价值等级 → 分群
        userSegment = profile.userProfile.select(
                        "user_id",
                        "action_count",
                        "purchase_count",
                        "has_purchase",
                        "user_active_level",
                        "pv_count",
                        "cart_count",
                        "fav_count",
                        "buy_count")
                .join(value.userValueProfile.select("user_id", "value_score", "user_value_level"),
                        "user_id", "left")
                .withColumn("user_segment",
                        when(col("has_purchase").equalTo("未购买用户")
                                        .and(col("cart_count").gt(0).or(col("fav_count").gt(0))),
                                "潜在购买用户")
                                .when(col("value_score").equalTo(0), "未购买用户")
                                .when(col("user_value_level").equalTo("高价值用户"), "高价值用户")
                                .when(col("purchase_count").gt(5), "高频购买用户")
                                .when(col("action_count").geq(200), "高活跃低购买用户")
                                .when(col("purchase_count").equalTo(1), "一次购买用户")
                                .otherwise("普通购买用户"));
        userSegment.show(10);

        // 缓存分群宽表，阶段七直接复用，避免重复构建整条 join 血缘
        userSegment.cache();
        userSegment.count();

        Dataset<Row> segmentSummary = userSegment.groupBy("user_segment")
                .count()
                .withColumnRenamed("count", "user_count")
                .orderBy(col("user_count").desc());
        segmentSummary.show(30);

        // 校验：分群后用户总数不变
        long segmentTotal = segmentSummary.selectExpr("sum(user_count) as total")
                .first().getLong(0);
        System.out.println("分群用户总数：" + segmentTotal +
                "（全量用户数：" + ctx.totalUserCount + "）");
        if (segmentTotal != ctx.totalUserCount) {
            throw new IllegalStateException("分群用户总数不一致: " + segmentTotal);
        }

        ResultExporter.exportTable(segmentSummary, ctx.config.outputDir, "segment_summary");
        // 全量用户分群明细（含用户 id 与画像字段），供报告与后续二次分析
        ResultExporter.exportCsv(userSegment, ctx.config.outputDir, "user_segment");
    }
}
