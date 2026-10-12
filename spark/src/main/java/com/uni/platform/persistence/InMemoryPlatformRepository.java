package com.uni.platform.persistence;

import com.uni.platform.model.PlatformModels.AnalysisJob;
import com.uni.platform.model.PlatformModels.DatasetRecord;
import com.uni.platform.model.PlatformModels.FieldMapping;
import com.uni.platform.model.PlatformModels.JobStatus;
import com.uni.platform.model.PlatformModels.ResultVersion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Explicit development/test repository. It is never selected as a MySQL fallback. */
public class InMemoryPlatformRepository implements PlatformRepository {
    private final AtomicLong datasetIds = new AtomicLong();
    private final AtomicLong jobIds = new AtomicLong();
    private final AtomicLong resultIds = new AtomicLong();
    private final Map<Long, DatasetRecord> datasets = new LinkedHashMap<>();
    private final Map<Long, List<FieldMapping>> mappings = new LinkedHashMap<>();
    private final Map<Long, AnalysisJob> jobs = new LinkedHashMap<>();
    private final Map<Long, ResultVersion> results = new LinkedHashMap<>();

    @Override
    public synchronized DatasetRecord createDataset(DatasetRecord value) {
        long id = datasetIds.incrementAndGet();
        DatasetRecord saved = new DatasetRecord(id, value.name(), value.description(),
                value.datasetType(), value.filePath(), value.fileSize(), value.rowCount(),
                value.status(), value.createdAt(), value.updatedAt(), value.lastAnalyzedAt(),
                value.hasHeader(), value.delimiter());
        datasets.put(id, saved);
        return saved;
    }

    @Override public synchronized DatasetRecord updateDataset(DatasetRecord value) {
        require(datasets, value.id(), "dataset");
        datasets.put(value.id(), value);
        return value;
    }
    @Override public synchronized Optional<DatasetRecord> findDataset(long id) { return Optional.ofNullable(datasets.get(id)); }
    @Override public synchronized List<DatasetRecord> listDatasets() { return List.copyOf(datasets.values()); }

    @Override public synchronized void replaceMappings(long datasetId, List<FieldMapping> values) {
        require(datasets, datasetId, "dataset");
        mappings.put(datasetId, List.copyOf(values));
    }
    @Override public synchronized List<FieldMapping> findMappings(long datasetId) {
        return mappings.getOrDefault(datasetId, List.of());
    }

    @Override public synchronized AnalysisJob createJob(AnalysisJob value) {
        require(datasets, value.datasetId(), "dataset");
        long id = jobIds.incrementAndGet();
        AnalysisJob saved = new AnalysisJob(id, value.datasetId(), value.status(),
                value.requestedModules(), value.logPath(), value.processId(), value.exitCode(),
                value.errorMessage(), value.createdAt(), value.startedAt(), value.finishedAt());
        jobs.put(id, saved);
        return saved;
    }
    @Override public synchronized AnalysisJob updateJob(AnalysisJob value) {
        require(jobs, value.id(), "job");
        jobs.put(value.id(), value);
        return value;
    }
    @Override public synchronized Optional<AnalysisJob> findJob(long id) { return Optional.ofNullable(jobs.get(id)); }
    @Override public synchronized List<AnalysisJob> listJobs() { return List.copyOf(jobs.values()); }

    @Override public synchronized ResultVersion addResult(ResultVersion value) {
        long id = resultIds.incrementAndGet();
        ResultVersion saved = new ResultVersion(id, value.datasetId(), value.jobId(),
                value.module(), value.resultPath(), value.createdAt());
        results.put(id, saved);
        return saved;
    }

    @Override public synchronized List<ResultVersion> findResults(long datasetId, long jobId) {
        return results.values().stream()
                .filter(r -> r.datasetId() == datasetId && r.jobId() == jobId)
                .sorted(Comparator.comparingLong(ResultVersion::id)).toList();
    }

    @Override public synchronized Optional<ResultVersion> findLatestSuccessfulResult(long datasetId, String module) {
        return results.values().stream()
                .filter(r -> r.datasetId() == datasetId && r.module().equals(module))
                .filter(r -> jobs.containsKey(r.jobId()) && jobs.get(r.jobId()).status() == JobStatus.SUCCESS)
                .max(Comparator.comparingLong(ResultVersion::id));
    }

    private static void require(Map<Long, ?> values, long id, String kind) {
        if (!values.containsKey(id)) throw new IllegalArgumentException(kind + " 不存在: " + id);
    }
}
