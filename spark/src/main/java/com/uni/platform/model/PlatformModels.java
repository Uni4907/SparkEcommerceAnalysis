package com.uni.platform.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Small immutable values shared by the V3 platform layers. */
public final class PlatformModels {
    private PlatformModels() {}

    public enum DatasetStatus { REGISTERED, MAPPING_REQUIRED, READY, ANALYZING, FAILED }
    public enum JobStatus { PENDING, RUNNING, SUCCESS, FAILED }

    public record DatasetRecord(
            long id,
            String name,
            String description,
            String datasetType,
            String filePath,
            long fileSize,
            long rowCount,
            DatasetStatus status,
            String createdAt,
            String updatedAt,
            String lastAnalyzedAt,
            boolean hasHeader,
            String delimiter) {

        public DatasetRecord withStatus(DatasetStatus next, String updatedAt) {
            return new DatasetRecord(id, name, description, datasetType, filePath,
                    fileSize, rowCount, next, createdAt, updatedAt, lastAnalyzedAt,
                    hasHeader, delimiter);
        }

        public DatasetRecord analyzed(String at) {
            return new DatasetRecord(id, name, description, datasetType, filePath,
                    fileSize, rowCount, DatasetStatus.READY, createdAt, at, at,
                    hasHeader, delimiter);
        }

        public DatasetRecord withRowCount(long count) {
            return new DatasetRecord(id, name, description, datasetType, filePath,
                    fileSize, count, status, createdAt, updatedAt, lastAnalyzedAt,
                    hasHeader, delimiter);
        }
    }

    public record FieldProfile(
            String sourceField,
            int sourceIndex,
            String inferredType,
            List<String> samples) {}

    public record FieldMapping(
            long datasetId,
            String sourceField,
            int sourceIndex,
            String standardField,
            String inferredType,
            double confidence,
            boolean confirmed,
            List<String> samples) {}

    public record CapabilityReport(
            long datasetId,
            Set<String> enabled,
            Map<String, List<String>> disabledReasons) {}

    public record AnalysisJob(
            long id,
            long datasetId,
            JobStatus status,
            String requestedModules,
            String logPath,
            Long processId,
            Integer exitCode,
            String errorMessage,
            String createdAt,
            String startedAt,
            String finishedAt) {

        public AnalysisJob running(long pid, String at) {
            return new AnalysisJob(id, datasetId, JobStatus.RUNNING, requestedModules,
                    logPath, pid, null, null, createdAt, at, null);
        }

        public AnalysisJob finished(JobStatus finalStatus, int code, String error, String at) {
            return new AnalysisJob(id, datasetId, finalStatus, requestedModules,
                    logPath, processId, code, error, createdAt, startedAt, at);
        }
    }

    public record ResultVersion(
            long id,
            long datasetId,
            long jobId,
            String module,
            String resultPath,
            String createdAt) {}

    public record RegistrationRequest(
            String name,
            String description,
            String datasetType,
            String filePath,
            Boolean hasHeader,
            String delimiter) {}

    public record JobRequest(long datasetId, List<String> modules) {}

    public record WorkerSpec(
            DatasetRecord dataset,
            List<FieldMapping> mappings,
            List<String> modules,
            String resultRoot,
            boolean hiveEnabled) {}

    public record WorkerManifest(
            long datasetId,
            long jobId,
            long rowCount,
            List<String> modules,
            Map<String, String> resultFiles,
            String completedAt) {}
}
