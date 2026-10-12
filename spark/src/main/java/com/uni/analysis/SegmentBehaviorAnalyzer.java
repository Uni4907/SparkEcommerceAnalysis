package com.uni.analysis;

import com.uni.AnalysisContext;
import com.uni.util.ResultExporter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.avg;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.lit;

/**
 * 阶段七：不同用户群体的行为差异分析。
 *
 * 以阶段六得到的用户群体为基础，统计每个群体的人均行为情况：
 *  - 行为总数 / 浏览 / 加购 / 收藏 / 购买
 *  - 购买商品范围（不同商品数、不同类别数）
 *  - 平均价值分
 *
 * 用于观察：高价值用户 vs 普通价值用户 vs 潜在用户等群体，
 * 在行为强度与购买转化上的差异。
 */
public class SegmentBehaviorAnalyzer {

    private final AnalysisContext ctx;
    private final UserSegmentAnalyzer segment;
    private final UserProfileAnalyzer profile;
    private final UserValueAnalyzer value;

    public SegmentBehaviorAnalyzer(AnalysisContext ctx,
                                   UserSegmentAnalyzer segment,
                                   UserProfileAnalyzer profile,
                                   UserValueAnalyzer value) {
        this.ctx = ctx;
        this.segment = segment;
        this.profile = profile;
        this.value = value;
    }

    public void run() {
        System.out.println("========== 阶段七：用户群体行为差异分析 ==========");

        // 行为计数统一来自缓存的用户画像宽表，避免重复聚合与缓存不一致
        Dataset<Row> base = segment.userSegment.select("user_id", "user_segment")
                .join(profile.userProfile.select(
                                "user_id",
                                "action_count",
                                "pv_count",
                                "cart_count",
                                "fav_count",
                                "buy_count",
                                "purchase_count",
                                "purchase_item_count",
                                "purchase_category_count"),
                        "user_id", "left")
                .join(value.userValueProfile.select("user_id", "value_score"),
                        "user_id", "left");

        Dataset<Row> segmentBehavior = base.groupBy("user_segment")
                .agg(
                        count(lit(1)).as("user_count"),
                        avg("action_count").as("avg_action_count"),
                        avg("pv_count").as("avg_pv_count"),
                        avg("cart_count").as("avg_cart_count"),
                        avg("fav_count").as("avg_fav_count"),
                        avg("buy_count").as("avg_buy_count"),
                        avg("purchase_count").as("avg_purchase_count"),
                        avg("purchase_item_count").as("avg_purchase_item_count"),
                        avg("purchase_category_count").as("avg_purchase_category_count"),
                        avg("value_score").as("avg_value_score")
                )
                .orderBy(col("user_count").desc());
        segmentBehavior.show(30, false);

        ResultExporter.exportTable(segmentBehavior, ctx.config.outputDir, "segment_behavior");
    }
}
