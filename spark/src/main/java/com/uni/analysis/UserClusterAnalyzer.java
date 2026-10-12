package com.uni.analysis;

import com.uni.AnalysisContext;
import com.uni.util.ResultExporter;
import org.apache.spark.ml.clustering.KMeans;
import org.apache.spark.ml.clustering.KMeansModel;
import org.apache.spark.ml.evaluation.ClusteringEvaluator;
import org.apache.spark.ml.feature.StandardScaler;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.List;

import static org.apache.spark.sql.functions.avg;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.log1p;

/**
 * 补充模块：基于用户画像的 KMeans 用户聚类（机器学习）。
 *
 * 与阶段六的"规则分群"形成对比：
 *  - 规则分群：根据业务阈值人工划分，结果可解释；
 *  - 本模块：从用户行为/购买特征自动聚类，用轮廓系数客观选择簇数。
 *
 * 处理流程：
 *  1. 选取 7 个画像特征（行为数、浏览/加购/收藏数、购买数、购买商品数、购买类别数）；
 *  2. 计数类特征呈长尾分布，先做 log1p 变换，再用 StandardScaler 标准化；
 *  3. 对 K = 3..7 分别训练 KMeans，记录训练代价(WSSSE)与轮廓系数；
 *  4. 选轮廓系数最高的 K 作为最终模型，输出用户所属聚类；
 *  5. 输出每个聚类的画像均值，以及"聚类 × 规则分群"交叉表，供报告解读。
 */
public class UserClusterAnalyzer {

    private final AnalysisContext ctx;
    private final UserProfileAnalyzer profile;
    private final UserValueAnalyzer value;
    private final UserSegmentAnalyzer segment;

    /** 参与聚类的画像特征（均为非负计数） */
    private static final String[] FEATURE_COLS = {
            "action_count",
            "pv_count",
            "cart_count",
            "fav_count",
            "purchase_count",
            "purchase_item_count",
            "purchase_category_count"
    };

    /** 尝试的簇数范围 */
    private static final int K_MIN = 3;
    private static final int K_MAX = 7;

    /** 轮廓系数评估抽样比例（全量 98 万用户抽样 20% 已足够稳定） */
    private static final double SIL_SAMPLE_RATIO = 0.2;

    public UserClusterAnalyzer(AnalysisContext ctx,
                               UserProfileAnalyzer profile,
                               UserValueAnalyzer value,
                               UserSegmentAnalyzer segment) {
        this.ctx = ctx;
        this.profile = profile;
        this.value = value;
        this.segment = segment;
    }

    public void run() {
        System.out.println("========== 补充模块：用户聚类（KMeans 机器学习） ==========");

        // 1) 特征宽表：画像 + 四类行为次数
        Dataset<Row> base = profile.userProfile.select(
                        "user_id",
                        "action_count",
                        "pv_count",
                        "cart_count",
                        "fav_count",
                        "purchase_count",
                        "purchase_item_count",
                        "purchase_category_count");

        // 2) 长尾计数特征做 log1p 变换，消除量纲差距
        Dataset<Row> logBase = base;
        List<String> logCols = new ArrayList<>();
        for (String feature : FEATURE_COLS) {
            String logCol = "log_" + feature;
            logCols.add(logCol);
            logBase = logBase.withColumn(logCol, log1p(col(feature)));
        }

        // 3) 向量化 + 标准化（消除特征方差差异）
        Dataset<Row> assembled = new VectorAssembler()
                .setInputCols(logCols.toArray(new String[0]))
                .setOutputCol("raw_features")
                .transform(logBase);

        Dataset<Row> scaled = new StandardScaler()
                .setInputCol("raw_features")
                .setOutputCol("scaled_features")
                .setWithStd(true)
                .setWithMean(false)
                .fit(assembled)
                .transform(assembled);

        // 缓存特征集：K 值实验与最终建模都要复用
        Dataset<Row> featureSet = scaled.select("user_id", "scaled_features");
        featureSet.cache();
        featureSet.count();

        // 轮廓系数评估用的抽样集
        Dataset<Row> silhouetteSample = featureSet
                .sample(false, SIL_SAMPLE_RATIO, 42L);
        silhouetteSample.cache();
        silhouetteSample.count();

        // 4) 实验 K=3..7：记录 WSSSE 训练代价与轮廓系数
        List<Object[]> evaluation = new ArrayList<>();
        int bestK = K_MIN;
        double bestSilhouette = -1;

        for (int k = K_MIN; k <= K_MAX; k++) {
            KMeansModel model = new KMeans()
                    .setK(k)
                    .setSeed(42L)
                    .setMaxIter(30)
                    .setFeaturesCol("scaled_features")
                    .setPredictionCol("cluster")
                    .fit(featureSet);

            double cost = model.summary().trainingCost();
            double silhouette = new ClusteringEvaluator()
                    .setFeaturesCol("scaled_features")
                    .setPredictionCol("cluster")
                    .evaluate(model.transform(silhouetteSample));

            evaluation.add(new Object[]{k, Math.round(cost * 100.0) / 100.0,
                    Math.round(silhouette * 10000.0) / 10000.0});
            System.out.println("K=" + k + " WSSSE=" + Math.round(cost) +
                    " 轮廓系数=" + String.format("%.4f", silhouette));

            if (silhouette > bestSilhouette) {
                bestSilhouette = silhouette;
                bestK = k;
            }
        }
        System.out.println("最终选择 K=" + bestK + "（轮廓系数最高）");

        // 5) 用最优 K 训练最终模型并输出聚类结果
        KMeansModel finalModel = new KMeans()
                .setK(bestK)
                .setSeed(42L)
                .setMaxIter(30)
                .setFeaturesCol("scaled_features")
                .setPredictionCol("cluster")
                .fit(featureSet);

        Dataset<Row> userCluster = finalModel.transform(featureSet)
                .select("user_id", "cluster")
                .orderBy("cluster", "user_id");
        userCluster.show(20, false);

        // 6) 每个聚类的画像均值（用原始特征，便于报告解读）
        Dataset<Row> clusterSummary = userCluster
                .join(base, "user_id", "inner")
                .groupBy("cluster")
                .agg(
                        count(lit(1)).as("user_count"),
                        avg("action_count").as("avg_action_count"),
                        avg("pv_count").as("avg_pv_count"),
                        avg("cart_count").as("avg_cart_count"),
                        avg("fav_count").as("avg_fav_count"),
                        avg("purchase_count").as("avg_purchase_count"),
                        avg("purchase_item_count").as("avg_purchase_item_count"),
                        avg("purchase_category_count").as("avg_purchase_category_count")
                )
                .orderBy("cluster");
        clusterSummary.show(30, false);

        // 7) 聚类 × 规则分群 交叉表，验证两类分群方法的一致性
        Dataset<Row> crossTable = userCluster
                .join(segment.userSegment.select("user_id", "user_segment"),
                        "user_id", "inner")
                .stat().crosstab("cluster", "user_segment");
        crossTable.show(30, false);

        // 导出
        ResultExporter.manualTable(ctx.config.outputDir, "cluster_evaluation",
                new String[]{"k", "wssse", "silhouette"}, evaluation);
        ResultExporter.exportTable(clusterSummary, ctx.config.outputDir, "cluster_summary");
        ResultExporter.exportTable(crossTable, ctx.config.outputDir, "cluster_segment_cross");
        ResultExporter.exportCsv(userCluster, ctx.config.outputDir, "user_cluster");
    }
}
