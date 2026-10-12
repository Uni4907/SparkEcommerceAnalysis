package com.uni.analysis;

import com.uni.AnalysisContext;
import com.uni.util.ResultExporter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.List;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.max;
import static org.apache.spark.sql.functions.min;
import static org.apache.spark.sql.functions.when;

/**
 * 阶段九：用户行为路径（转化链路）分析。
 *
 * 数据集中没有"会话 ID"，无法还原一次完整会话内的每一步顺序。
 * 因此本模块采用可解释的近似口径：
 *  以 (user_id, item_id) 为分析单元，如果同一用户对同一商品在购买事件前
 *  （时间戳 ≤ 该商品对最后一次购买时间）发生过浏览/加购/收藏，
 *  则认为该"用户-商品"对具备对应行为到购买的可构成链路。
 *
 * 统计并输出：
 *  购买商品对总数、其中购买前有浏览 / 加购 / 收藏的对数，
 *  以及"浏览→加购→购买""浏览→收藏→购买"完整链路的对数与去重用户数。
 *
 * 该口径会明确写入报告，不会伪装成严格会话级漏斗。
 */
public class BehaviorPathAnalyzer {

    private final AnalysisContext ctx;

    public BehaviorPathAnalyzer(AnalysisContext ctx) {
        this.ctx = ctx;
    }

    public void run() {
        System.out.println("========== 阶段九：用户行为路径（链路）分析 ==========");
        System.out.println("说明：数据无会话ID，按(用户,商品)对近似统计购买前置行为，"
                + "详见代码注释与报告。");

        // 购买行为构成的所有(用户,商品)对及其最大购买时间
        Dataset<Row> buyPair = ctx.buyData
                .groupBy("user_id", "item_id")
                .agg(max("timestamp").as("buy_max_ts"));

        long buyPairCount = buyPair.count();
        long buyUserCount = buyPair.select("user_id").distinct().count();
        System.out.println("购买商品对总数：" + buyPairCount
                + "，去重购买用户：" + buyUserCount);

        // 各行为在"用户-商品"维度上的最早发生时间
        Dataset<Row> pvAgg = earliestBehaviorTime("pv");
        Dataset<Row> cartAgg = earliestBehaviorTime("cart");
        Dataset<Row> favAgg = earliestBehaviorTime("fav");

        // 为每个购买商品对打标记：购买前（该行为最早时间 ≤ 该对最后购买时间）
        // 是否发生过 浏览/加购/收藏；单表结构避免了派生表自连接的列歧义
        Dataset<Row> flags = buyPair
                .join(pvAgg, new String[]{"user_id", "item_id"}, "left")
                .join(cartAgg, new String[]{"user_id", "item_id"}, "left")
                .join(favAgg, new String[]{"user_id", "item_id"}, "left")
                .withColumn("has_pv_early",
                        when(col("pv_min_ts").isNotNull()
                                .and(col("pv_min_ts").leq(col("buy_max_ts"))), true)
                                .otherwise(false))
                .withColumn("has_cart_early",
                        when(col("cart_min_ts").isNotNull()
                                .and(col("cart_min_ts").leq(col("buy_max_ts"))), true)
                                .otherwise(false))
                .withColumn("has_fav_early",
                        when(col("fav_min_ts").isNotNull()
                                .and(col("fav_min_ts").leq(col("buy_max_ts"))), true)
                                .otherwise(false));

        Dataset<Row> pvEarly = flags.filter(col("has_pv_early"))
                .select("user_id", "item_id");
        Dataset<Row> cartEarly = flags.filter(col("has_cart_early"))
                .select("user_id", "item_id");
        Dataset<Row> favEarly = flags.filter(col("has_fav_early"))
                .select("user_id", "item_id");

        long pvPairCount = pvEarly.count();
        long cartPairCount = cartEarly.count();
        long favPairCount = favEarly.count();

        // 完整链路：浏览+加购+购买 / 浏览+收藏+购买
        Dataset<Row> pvCartBuy = flags
                .filter(col("has_pv_early").and(col("has_cart_early")))
                .select("user_id", "item_id");
        Dataset<Row> pvFavBuy = flags
                .filter(col("has_pv_early").and(col("has_fav_early")))
                .select("user_id", "item_id");
        long pvCartBuyCount = pvCartBuy.count();
        long pvFavBuyCount = pvFavBuy.count();

        System.out.println("购买前有浏览的商品对：" + pvPairCount);
        System.out.println("购买前有加购的商品对：" + cartPairCount);
        System.out.println("购买前有收藏的商品对：" + favPairCount);
        System.out.println("具备 浏览→加购→购买 链路的商品对：" + pvCartBuyCount);
        System.out.println("具备 浏览→收藏→购买 链路的商品对：" + pvFavBuyCount);

        // 汇总导出（列名使用英文便于前端 JSON 处理）
        String[] cols = {"stage", "pair_count", "user_count", "pair_percent"};
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"购买商品对", buyPairCount, buyUserCount, 100.0});
        addFunnelRow(rows, "购买前有浏览(浏览→购买)", pvPairCount,
                distinctUsers(pvEarly), buyPairCount);
        addFunnelRow(rows, "购买前有加购(加购→购买)", cartPairCount,
                distinctUsers(cartEarly), buyPairCount);
        addFunnelRow(rows, "购买前有收藏(收藏→购买)", favPairCount,
                distinctUsers(favEarly), buyPairCount);
        addFunnelRow(rows, "浏览→加购→购买", pvCartBuyCount,
                distinctUsers(pvCartBuy), buyPairCount);
        addFunnelRow(rows, "浏览→收藏→购买", pvFavBuyCount,
                distinctUsers(pvFavBuy), buyPairCount);
        ResultExporter.manualTable(ctx.config.outputDir, "behavior_path_funnel", cols, rows);

        // 保存各阶段(用户,商品)对集合，便于报告做进一步验证
        ResultExporter.exportCsv(pvEarly.withColumn("stage", lit("pv_before_buy")),
                ctx.config.outputDir, "path_pv_before_buy");
        ResultExporter.exportCsv(cartEarly.withColumn("stage", lit("cart_before_buy")),
                ctx.config.outputDir, "path_cart_before_buy");
        ResultExporter.exportCsv(favEarly.withColumn("stage", lit("fav_before_buy")),
                ctx.config.outputDir, "path_fav_before_buy");
    }

    /** 统计某种行为在每个(用户,商品)上的最早发生时间 */
    private Dataset<Row> earliestBehaviorTime(String behavior) {
        Dataset<Row> behaviors = ctx.df
                .filter(col("behavior").equalTo(lit(behavior)))
                .select(
                        col("user_id"),
                        col("item_id"),
                        col("timestamp"));
        String minColumn = behavior + "_min_ts";
        return behaviors.groupBy("user_id", "item_id")
                .agg(min("timestamp").as(minColumn));
    }

    private static long distinctUsers(Dataset<Row> pairs) {
        return pairs.select("user_id").distinct().count();
    }

    private static void addFunnelRow(List<Object[]> rows, String stage,
                                     long pairCount, long userCount, long buyPairCount) {
        rows.add(new Object[]{
                stage,
                pairCount,
                userCount,
                Math.round(pairCount * 10000.0 / buyPairCount) / 100.0
        });
    }
}
