package com.uni.analysis;

import com.uni.AnalysisContext;
import com.uni.util.ResultExporter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.countDistinct;
import static org.apache.spark.sql.functions.datediff;
import static org.apache.spark.sql.functions.from_unixtime;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.max;
import static org.apache.spark.sql.functions.sum;
import static org.apache.spark.sql.functions.when;

/**
 * 阶段四：用户画像宽表。
 *
 * 基于阶段三的用户行为统计，进一步统计：
 *  - 购买过的不同商品数 purchase_item_count
 *  - 购买涉及的不同类别数 purchase_category_count
 *  - 最后购买时间 last_purchase_time 与距最新购买日的天数
 *  - 是否存在购买行为 has_purchase
 *
 * 字段构成与 NULL 填充规则与原 Main.java 保持一致，
 * 并校验画像用户总数 = 全量用户总数（987,994）。
 */
public class UserProfileAnalyzer {

    private final AnalysisContext ctx;
    private final UserActivityAnalyzer activity;

    /** 用户画像宽表 */
    public Dataset<Row> userProfile;

    public UserProfileAnalyzer(AnalysisContext ctx, UserActivityAnalyzer activity) {
        this.ctx = ctx;
        this.activity = activity;
    }

    public void run() {
        System.out.println("========== 阶段四：用户画像宽表 ==========");

        Dataset<Row> buyData = ctx.buyData;

        // 用户购买过的不同商品数
        Dataset<Row> userPurchaseItemCount = buyData.groupBy("user_id")
                .agg(countDistinct("item_id"))
                .withColumnRenamed("count(DISTINCT item_id)", "purchase_item_count");

        // 用户购买涉及的不同类别数
        Dataset<Row> userPurchaseCategoryCount = buyData.groupBy("user_id")
                .agg(countDistinct("category_id"))
                .withColumnRenamed("count(DISTINCT category_id)", "purchase_category_count");

        // 每个用户四种行为的事件次数（并入画像宽表作为唯一数据源，
        // 避免后续分群/聚类模块各自重新聚合导致缓存不一致）
        Dataset<Row> userBehaviorCounts = ctx.df.groupBy("user_id")
                .agg(
                        sum(when(col("behavior").equalTo("pv"), 1).otherwise(0)).as("pv_count"),
                        sum(when(col("behavior").equalTo("cart"), 1).otherwise(0)).as("cart_count"),
                        sum(when(col("behavior").equalTo("fav"), 1).otherwise(0)).as("fav_count"),
                        sum(when(col("behavior").equalTo("buy"), 1).otherwise(0)).as("buy_count")
                );

        // 购买数据中的最新购买时间戳（沿用原代码：以 buyData 最大时间戳为分析参考时间）
        long maxPurchaseTimestamp = buyData.select(max("timestamp"))
                .first().getLong(0);
        System.out.println("购买数据最大时间戳：" + maxPurchaseTimestamp);

        // 每个购买用户的最后购买时间与距最新购买日天数
        Dataset<Row> userLastPurchaseTime = buyData.groupBy("user_id")
                .max("timestamp")
                .withColumnRenamed("max(timestamp)", "last_purchase_timestamp")
                .withColumn("last_purchase_time",
                        from_unixtime(col("last_purchase_timestamp")))
                .withColumn("days_since_last_purchase",
                        datediff(from_unixtime(lit(maxPurchaseTimestamp)),
                                from_unixtime(col("last_purchase_timestamp"))));
        userLastPurchaseTime.show(10);

        // 用户画像宽表：活跃度 × 购买次数 × 商品范围 × 类别范围 × 最后购买时间
        userProfile = activity.userActionCount
                .join(activity.userPurchaseCount, "user_id", "left")
                .join(userPurchaseItemCount, "user_id", "left")
                .join(userPurchaseCategoryCount, "user_id", "left")
                .join(userLastPurchaseTime, "user_id", "left")
                .join(userBehaviorCounts, "user_id", "left")
                .na().fill(0, new String[]{
                        "purchase_count",
                        "purchase_item_count",
                        "purchase_category_count"
                })
                .withColumn("user_active_level",
                        when(col("action_count").lt(50), "低活跃用户")
                                .when(col("action_count").lt(200), "普通用户")
                                .otherwise("高活跃用户"))
                .withColumn("has_purchase",
                        when(col("purchase_count").gt(0), "已购买用户")
                                .otherwise("未购买用户"));
        userProfile.show(10);

        // 数据质量校验：画像行数必须等于全量用户数
        userProfile.cache();
        long profileCount = userProfile.count();
        long totalUserCount = ctx.totalUserCount;
        System.out.println("用户画像数量：" + profileCount);
        System.out.println("全量用户数量：" + totalUserCount);
        if (profileCount != totalUserCount) {
            throw new IllegalStateException("用户画像数据质量校验失败：" +
                    profileCount + " != " + totalUserCount);
        }
        // 行为计数不允许为空（新增数据质量校验）
        long nullBehaviorCount = userProfile
                .filter(col("pv_count").isNull()
                        .or(col("cart_count").isNull())
                        .or(col("fav_count").isNull())
                        .or(col("buy_count").isNull()))
                .count();
        if (nullBehaviorCount > 0) {
            throw new IllegalStateException("用户画像行为计数存在空值: " + nullBehaviorCount);
        }

        // 导出用户画像宽表（行数约 98 万，仅导出 CSV，供后续分析/报告使用）
        ResultExporter.exportCsv(userProfile, ctx.config.outputDir, "user_profile");
    }
}
