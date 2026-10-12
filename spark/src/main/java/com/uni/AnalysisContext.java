package com.uni;

import com.uni.config.AnalysisConfig;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次完整分析运行中共享的上下文对象。
 * 集中保存 SparkSession、运行配置以及被缓存的核心 DataFrame，
 * 避免各分析模块之间重复加载数据。
 */
public class AnalysisContext {

    public final SparkSession spark;
    public final AnalysisConfig config;

    /** 原始全量行为数据（已缓存） */
    public Dataset<Row> df;

    /** 购买行为数据（已缓存） */
    public Dataset<Row> buyData;

    /** 全量用户数（阶段二统计后保存，供各模块复用，避免重复全表扫描） */
    public long totalUserCount;

    /** 全量行为记录数 */
    public long totalBehaviorCount;

    /** 各阶段耗时记录（用于输出 performance_stats 表） */
    public final List<Object[]> performanceRows = new ArrayList<>();

    public AnalysisContext(SparkSession spark, AnalysisConfig config) {
        this.spark = spark;
        this.config = config;
    }

    /** 记录某个阶段的耗时（秒） */
    public void recordPerformance(String name, double seconds) {
        double rounded = Math.round(seconds * 100.0) / 100.0;
        performanceRows.add(new Object[]{name, rounded});
        System.out.println("[计时] " + name + "：" + rounded + " 秒");
    }
}
