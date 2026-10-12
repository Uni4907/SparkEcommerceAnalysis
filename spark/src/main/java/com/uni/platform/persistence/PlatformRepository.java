package com.uni.platform.persistence;

import com.uni.platform.model.PlatformModels.AnalysisJob;
import com.uni.platform.model.PlatformModels.DatasetRecord;
import com.uni.platform.model.PlatformModels.FieldMapping;
import com.uni.platform.model.PlatformModels.ResultVersion;

import java.util.List;
import java.util.Optional;

public interface PlatformRepository extends AutoCloseable {
    DatasetRecord createDataset(DatasetRecord dataset);
    DatasetRecord updateDataset(DatasetRecord dataset);
    Optional<DatasetRecord> findDataset(long id);
    List<DatasetRecord> listDatasets();

    void replaceMappings(long datasetId, List<FieldMapping> mappings);
    List<FieldMapping> findMappings(long datasetId);

    AnalysisJob createJob(AnalysisJob job);
    AnalysisJob updateJob(AnalysisJob job);
    Optional<AnalysisJob> findJob(long id);
    List<AnalysisJob> listJobs();

    ResultVersion addResult(ResultVersion result);
    List<ResultVersion> findResults(long datasetId, long jobId);
    Optional<ResultVersion> findLatestSuccessfulResult(long datasetId, String module);

    @Override
    default void close() {}
}
