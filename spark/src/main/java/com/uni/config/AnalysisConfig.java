package com.uni.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 分析运行配置。
 *
 * 通过 JVM 系统属性配置，方便命令行 / IDEA 运行时灵活指定：
 *   -Dspark.data.path  原始数据 CSV 路径（默认指向桌面原项目的数据文件）
 *   -Dspark.output.dir 结果输出目录（默认当前工作目录下的 output）
 *   -Dspark.top.n      各类排行榜展示条数（默认 10）
 */
public class AnalysisConfig {

    /** 默认数据路径：与原 Main.java 中的硬编码路径保持一致 */
    public static final String DEFAULT_DATA_PATH =
            "../sample-data/sample_taobao_behavior.csv";

    public final String dataPath;
    public final Path outputDir;
    public final int topN;
    /** 是否执行"缓存前后耗时对比"基准测试（会额外读取一次全量 CSV） */
    public final boolean cacheBenchmark;

    private AnalysisConfig(String dataPath, Path outputDir, int topN, boolean cacheBenchmark) {
        this.dataPath = dataPath;
        this.outputDir = outputDir;
        this.topN = topN;
        this.cacheBenchmark = cacheBenchmark;
    }

    public static AnalysisConfig load() {
        String dataPath = System.getProperty("spark.data.path", DEFAULT_DATA_PATH);
        String outputDir = System.getProperty(
                "spark.output.dir",
                Paths.get(System.getProperty("user.dir"), "output").toString()
        );
        int topN = Integer.getInteger("spark.top.n", 10);
        boolean cacheBenchmark = Boolean.parseBoolean(
                System.getProperty("spark.benchmark.cache", "true"));
        return new AnalysisConfig(dataPath, Paths.get(outputDir), topN, cacheBenchmark);
    }

    @Override
    public String toString() {
        return "AnalysisConfig{" +
                "dataPath='" + dataPath + '\'' +
                ", outputDir=" + outputDir +
                ", topN=" + topN +
                '}';
    }
}
