package com.uni.platform.result;

import com.uni.util.ResultExporter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.apache.spark.sql.functions.lit;

public class VersionedResultWriter {
    private final long datasetId;
    private final long jobId;
    private final Path jobOutput;
    private final List<String> modules = new ArrayList<>();

    public VersionedResultWriter(long datasetId, long jobId, Path resultRoot) {
        this.datasetId = datasetId;
        this.jobId = jobId;
        this.jobOutput = resultRoot.resolve(Long.toString(datasetId)).resolve(Long.toString(jobId));
    }

    public void table(String module, Dataset<Row> data) {
        Dataset<Row> versioned = data.withColumn("dataset_id", lit(datasetId))
                .withColumn("job_id", lit(jobId));
        ResultExporter.exportTable(versioned, jobOutput, module);
        modules.add(module);
    }

    public void manual(String module, String[] columns, List<Object[]> rows) {
        String[] withIds = new String[columns.length + 2];
        withIds[0] = "dataset_id"; withIds[1] = "job_id";
        System.arraycopy(columns, 0, withIds, 2, columns.length);
        List<Object[]> versioned = new ArrayList<>();
        for (Object[] row : rows) {
            Object[] value = new Object[row.length + 2];
            value[0] = datasetId; value[1] = jobId;
            System.arraycopy(row, 0, value, 2, row.length);
            versioned.add(value);
        }
        ResultExporter.manualTable(jobOutput, module, withIds, versioned);
        modules.add(module);
    }

    public Path jobOutput() { return jobOutput; }
    public List<String> modules() { return List.copyOf(modules); }
    public Path jsonPath(String module) { return jobOutput.resolve("data").resolve(module + ".json"); }
}
