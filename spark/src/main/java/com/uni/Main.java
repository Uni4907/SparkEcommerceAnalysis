package com.uni;

import com.uni.analysis.BehaviorPathAnalyzer;
import com.uni.analysis.DataQualityAnalyzer;
import com.uni.analysis.ProductAnalyzer;
import com.uni.analysis.SegmentBehaviorAnalyzer;
import com.uni.analysis.TimeTrendAnalyzer;
import com.uni.analysis.UserActivityAnalyzer;
import com.uni.analysis.UserClusterAnalyzer;
import com.uni.analysis.UserProfileAnalyzer;
import com.uni.analysis.UserSegmentAnalyzer;
import com.uni.analysis.UserValueAnalyzer;
import com.uni.config.AnalysisConfig;
import com.uni.util.ResultExporter;
import com.uni.util.WebAssets;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import static org.apache.spark.sql.functions.col;

/**
 * Spark 电商用户分析与可视化系统 - 主程序入口。
 *
 * 运行阶段总览：
 *  阶段一/二 数据读取、总体统计与行为占比（DataQualityAnalyzer）
 *  阶段三    用户行为与购买力分析（UserActivityAnalyzer）
 *  阶段四    用户画像宽表（UserProfileAnalyzer）
 *  阶段五    用户价值分析 A/F/R（UserValueAnalyzer）
 *  阶段六    用户分群（UserSegmentAnalyzer）
 *  阶段七    用户群体行为差异分析（SegmentBehaviorAnalyzer）
 *  阶段八    商品分析（ProductAnalyzer）
 *  阶段九    用户行为路径/链路分析（BehaviorPathAnalyzer）
 *  阶段十    时间维度趋势分析（TimeTrendAnalyzer）
 *
 * 每个模块的分析结果会导出到 output/data 目录（CSV + JSON），
 * 前端可视化使用 output 目录下的静态页面，可用 DashboardServer 启动查看。
 *
 * 常用运行参数（JVM 系统属性）：
 *  -Dspark.data.path=xxx    原始数据 CSV 路径
 *  -Dspark.output.dir=xxx   结果输出目录
 *  -Dspark.top.n=10         排行榜数量
 */
public class Main {

    public static void main(String[] args) {
        AnalysisConfig config = AnalysisConfig.load();
        System.out.println("运行配置：" + config);

        SparkSession spark = SparkSession.builder()
                .appName("SparkEcommerceAnalysis")
                .master("local[*]")
                // 数据集时间戳按北京时间统计（from_unixtime / to_date 均受该设置影响）
                .config("spark.sql.session.timeZone", "Asia/Shanghai")
                .config("spark.sql.warehouse.dir",
                        config.outputDir.resolve("spark-warehouse").toString())
                .getOrCreate();

        AnalysisContext ctx = new AnalysisContext(spark, config);

        try {
            long loadStart = System.currentTimeMillis();
            loadAndCacheData(ctx);
            ctx.recordPerformance("数据读取与缓存(CSV → DataFrame)",
                    (System.currentTimeMillis() - loadStart) / 1000.0);

            // ============ 阶段一/二 ============
            runTimed(ctx, "阶段一/二 总体统计与行为占比",
                    () -> new DataQualityAnalyzer(ctx).run());

            // ============ 阶段三 ============
            UserActivityAnalyzer activityAnalyzer = new UserActivityAnalyzer(ctx);
            runTimed(ctx, "阶段三 用户活跃度与购买力分析", activityAnalyzer::run);

            // ============ 阶段四 ============
            UserProfileAnalyzer profileAnalyzer =
                    new UserProfileAnalyzer(ctx, activityAnalyzer);
            runTimed(ctx, "阶段四 用户画像宽表", profileAnalyzer::run);

            // ============ 阶段五 ============
            UserValueAnalyzer valueAnalyzer =
                    new UserValueAnalyzer(ctx, profileAnalyzer);
            runTimed(ctx, "阶段五 用户价值分析(A/F/R)", valueAnalyzer::run);

            // ============ 阶段六 ============
            UserSegmentAnalyzer segmentAnalyzer =
                    new UserSegmentAnalyzer(ctx, profileAnalyzer, valueAnalyzer);
            runTimed(ctx, "阶段六 用户规则分群", segmentAnalyzer::run);

            // ============ 阶段七 ============
            runTimed(ctx, "阶段七 用户群体行为差异分析",
                    () -> new SegmentBehaviorAnalyzer(
                            ctx, segmentAnalyzer, profileAnalyzer, valueAnalyzer).run());

            // ============ 补充模块：KMeans 用户聚类 ============
            runTimed(ctx, "补充模块 用户聚类(KMeans)",
                    () -> new UserClusterAnalyzer(
                            ctx, profileAnalyzer, valueAnalyzer, segmentAnalyzer).run());

            // ============ 阶段八 ============
            runTimed(ctx, "阶段八 商品分析", () -> new ProductAnalyzer(ctx).run());

            // ============ 阶段九 ============
            runTimed(ctx, "阶段九 用户行为路径分析",
                    () -> new BehaviorPathAnalyzer(ctx).run());

            // ============ 阶段十 ============
            runTimed(ctx, "阶段十 时间维度趋势分析",
                    () -> new TimeTrendAnalyzer(ctx).run());

            // ============ 性能验证：缓存前后耗时对比 ============
            if (config.cacheBenchmark) {
                runCacheBenchmark(ctx);
            }

            ResultExporter.manualTable(ctx.config.outputDir, "performance_stats",
                    new String[]{"metric", "value"}, ctx.performanceRows);

            // 复制可视化静态资源到输出目录
            WebAssets.copyTo(config.outputDir);

            System.out.println();
            System.out.println("========================================================");
            System.out.println("全部分析完成，结果位于：" + config.outputDir.toAbsolutePath());
            System.out.println("可视化启动：运行 com.uni.dashboard.DashboardServer 后访问");
            System.out.println("http://127.0.0.1:8090/index.html");
            System.out.println("========================================================");
        } finally {
            spark.stop();
        }
    }

    /**
     * 加载并缓存原始数据（与原 Main.java 的读取逻辑一致）：
     * CSV 无表头 + inferSchema，补充列名，时间戳转 long，全量与购买数据缓存。
     */
    private static void loadAndCacheData(AnalysisContext ctx) {
        Dataset<Row> df = readCsv(ctx.spark, ctx.config.dataPath);
        df.cache();

        Dataset<Row> buyData = df.filter(col("behavior").equalTo("buy"));
        buyData.cache();

        // 触发一次 action，确保缓存真正落地后再开始后续多轮分析
        buyData.count();

        ctx.df = df;
        ctx.buyData = buyData;
    }

    /** 读取原始 CSV 并做与原代码一致的列名/类型处理 */
    private static Dataset<Row> readCsv(SparkSession spark, String dataPath) {
        Dataset<Row> df = spark.read()
                .option("header", "false")
                .option("inferSchema", "true")
                .csv(dataPath);

        df = df.toDF(
                "user_id",
                "item_id",
                "category_id",
                "behavior",
                "timestamp"
        );

        return df.withColumn("timestamp", col("timestamp").cast("long"));
    }

    /** 给阶段计时并记录 */
    private static void runTimed(AnalysisContext ctx, String name, Runnable task) {
        long start = System.currentTimeMillis();
        task.run();
        ctx.recordPerformance(name, (System.currentTimeMillis() - start) / 1000.0);
    }

    /**
     * 缓存优化效果验证：
     * 对同一个"按行为分组统计"任务，分别测量
     *  ① 未缓存：每次从磁盘重新读取全量 CSV 再聚合
     *  ② 已缓存：直接复用 Spark 缓存中的数据聚合
     * 两个结果数量应完全一致，用真实耗时证明缓存对重复分析的价值。
     */
    private static void runCacheBenchmark(AnalysisContext ctx) {
        System.out.println("========== 性能验证：缓存前后耗时对比 ==========");

        Dataset<Row> fresh = readCsv(ctx.spark, ctx.config.dataPath);

        long start = System.currentTimeMillis();
        long freshCount = fresh.groupBy("behavior").count().count();
        long uncachedMs = System.currentTimeMillis() - start;
        System.out.println("未缓存：全量读取并聚合耗时 " + uncachedMs + " ms");

        start = System.currentTimeMillis();
        long cachedCount = ctx.df.groupBy("behavior").count().count();
        long cachedMs = System.currentTimeMillis() - start;
        System.out.println("已缓存：聚合耗时 " + cachedMs + " ms");

        if (freshCount != cachedCount) {
            throw new IllegalStateException("缓存对比结果不一致: " +
                    freshCount + " vs " + cachedCount);
        }

        ctx.recordPerformance("未缓存：全量读取+行为分组聚合(秒)", uncachedMs / 1000.0);
        ctx.recordPerformance("已缓存：行为分组聚合(秒)", cachedMs / 1000.0);
    }
}
