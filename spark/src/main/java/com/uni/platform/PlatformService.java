package com.uni.platform;

import com.uni.platform.analysis.AnalysisRegistry;
import com.uni.platform.config.PlatformConfig;
import com.uni.platform.dataset.DatasetManager;
import com.uni.platform.job.JobManager;
import com.uni.platform.model.PlatformModels.AnalysisJob;
import com.uni.platform.model.PlatformModels.CapabilityReport;
import com.uni.platform.model.PlatformModels.DatasetRecord;
import com.uni.platform.model.PlatformModels.FieldMapping;
import com.uni.platform.model.PlatformModels.JobRequest;
import com.uni.platform.model.PlatformModels.ResultVersion;
import com.uni.platform.persistence.InMemoryPlatformRepository;
import com.uni.platform.persistence.JdbcPlatformRepository;
import com.uni.platform.persistence.PlatformRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

public class PlatformService implements AutoCloseable {
    private final PlatformConfig config;
    private final PlatformRepository repository;
    private final DatasetManager datasets;
    private final AnalysisRegistry analyses = new AnalysisRegistry();
    private final JobManager jobs;

    public PlatformService(PlatformConfig config) throws IOException {
        this.config = config;
        config.validate();
        Files.createDirectories(config.dataDir());
        Files.createDirectories(config.resultDir());
        Files.createDirectories(config.jobDir());
        if (config.repositoryMode().equals("mysql")) {
            JdbcPlatformRepository jdbc = new JdbcPlatformRepository(config.jdbcUrl(), config.jdbcUser(), config.jdbcPassword());
            jdbc.initializeSchema();
            this.repository = jdbc;
        } else {
            this.repository = new InMemoryPlatformRepository();
        }
        this.datasets = new DatasetManager(repository);
        this.jobs = new JobManager(repository, config);
    }

    public PlatformConfig config() { return config; }
    public DatasetManager datasets() { return datasets; }
    public JobManager jobs() { return jobs; }
    public PlatformRepository repository() { return repository; }

    public AnalysisJob submit(JobRequest request) throws IOException {
        DatasetRecord dataset = datasets.get(request.datasetId());
        CapabilityReport report = datasets.capabilities(dataset.id());
        List<String> modules = analyses.validateRequested(request.modules(), report);
        boolean hive = Boolean.getBoolean("platform.hive.enabled");
        return jobs.submit(dataset, repository.findMappings(dataset.id()), modules, hive);
    }

    public ResultVersion latest(long datasetId, String module) {
        datasets.get(datasetId);
        return repository.findLatestSuccessfulResult(datasetId, module)
                .orElseThrow(() -> new IllegalArgumentException("没有 SUCCESS 结果: dataset=" + datasetId + ", module=" + module));
    }

    @Override public void close() {
        jobs.close();
        repository.close();
    }
}
