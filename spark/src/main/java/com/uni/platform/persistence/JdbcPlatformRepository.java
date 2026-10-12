package com.uni.platform.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uni.platform.model.PlatformModels.AnalysisJob;
import com.uni.platform.model.PlatformModels.DatasetRecord;
import com.uni.platform.model.PlatformModels.DatasetStatus;
import com.uni.platform.model.PlatformModels.FieldMapping;
import com.uni.platform.model.PlatformModels.JobStatus;
import com.uni.platform.model.PlatformModels.ResultVersion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** MySQL repository. Connection failures are propagated; there is no memory fallback. */
public class JdbcPlatformRepository implements PlatformRepository {
    private final String url;
    private final String user;
    private final String password;
    private final ObjectMapper json = new ObjectMapper();

    public JdbcPlatformRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public void initializeSchema() {
        String[] ddl = {
                "CREATE TABLE IF NOT EXISTS dataset (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(255) NOT NULL, description TEXT NOT NULL, dataset_type VARCHAR(64) NOT NULL, file_path TEXT NOT NULL, file_size BIGINT NOT NULL, row_count BIGINT NOT NULL DEFAULT -1, status VARCHAR(32) NOT NULL, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL, last_analyzed_at TIMESTAMP(3) NULL, has_header BOOLEAN NOT NULL, delimiter_char VARCHAR(8) NOT NULL)",
                "CREATE TABLE IF NOT EXISTS dataset_field_mapping (dataset_id BIGINT NOT NULL, source_field VARCHAR(255) NOT NULL, source_index INT NOT NULL, standard_field VARCHAR(64) NOT NULL, inferred_type VARCHAR(32) NOT NULL, confidence DOUBLE NOT NULL, confirmed BOOLEAN NOT NULL, samples_json TEXT NOT NULL, PRIMARY KEY(dataset_id, source_index), CONSTRAINT fk_mapping_dataset FOREIGN KEY(dataset_id) REFERENCES dataset(id))",
                "CREATE TABLE IF NOT EXISTS analysis_job (id BIGINT PRIMARY KEY AUTO_INCREMENT, dataset_id BIGINT NOT NULL, status VARCHAR(32) NOT NULL, requested_modules TEXT NOT NULL, log_path TEXT NOT NULL, process_id BIGINT NULL, exit_code INT NULL, error_message TEXT NULL, created_at TIMESTAMP(3) NOT NULL, started_at TIMESTAMP(3) NULL, finished_at TIMESTAMP(3) NULL, INDEX idx_job_dataset_status(dataset_id,status), CONSTRAINT fk_job_dataset FOREIGN KEY(dataset_id) REFERENCES dataset(id))",
                "CREATE TABLE IF NOT EXISTS result_version (id BIGINT PRIMARY KEY AUTO_INCREMENT, dataset_id BIGINT NOT NULL, job_id BIGINT NOT NULL, module_name VARCHAR(64) NOT NULL, result_path TEXT NOT NULL, created_at TIMESTAMP(3) NOT NULL, INDEX idx_result_latest(dataset_id,module_name,id), CONSTRAINT fk_result_dataset FOREIGN KEY(dataset_id) REFERENCES dataset(id), CONSTRAINT fk_result_job FOREIGN KEY(job_id) REFERENCES analysis_job(id))"
        };
        try (Connection c = connection(); Statement s = c.createStatement()) {
            for (String sql : ddl) s.execute(sql);
        } catch (SQLException e) {
            throw failure("初始化 MySQL schema", e);
        }
    }

    @Override public DatasetRecord createDataset(DatasetRecord d) {
        String sql = "INSERT INTO dataset(name,description,dataset_type,file_path,file_size,row_count,status,created_at,updated_at,last_analyzed_at,has_header,delimiter_char) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = connection(); PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindDataset(p, d, false); p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) {
                if (!r.next()) throw new SQLException("未返回 dataset id");
                return new DatasetRecord(r.getLong(1), d.name(), d.description(), d.datasetType(), d.filePath(),
                        d.fileSize(), d.rowCount(), d.status(), d.createdAt(), d.updatedAt(), d.lastAnalyzedAt(), d.hasHeader(), d.delimiter());
            }
        } catch (SQLException e) { throw failure("创建 dataset", e); }
    }

    @Override public DatasetRecord updateDataset(DatasetRecord d) {
        String sql = "UPDATE dataset SET name=?,description=?,dataset_type=?,file_path=?,file_size=?,row_count=?,status=?,created_at=?,updated_at=?,last_analyzed_at=?,has_header=?,delimiter_char=? WHERE id=?";
        try (Connection c = connection(); PreparedStatement p = c.prepareStatement(sql)) {
            bindDataset(p, d, true);
            if (p.executeUpdate() != 1) throw new IllegalArgumentException("dataset 不存在: " + d.id());
            return d;
        } catch (SQLException e) { throw failure("更新 dataset", e); }
    }

    private static void bindDataset(PreparedStatement p, DatasetRecord d, boolean id) throws SQLException {
        p.setString(1, d.name()); p.setString(2, d.description()); p.setString(3, d.datasetType());
        p.setString(4, d.filePath()); p.setLong(5, d.fileSize()); p.setLong(6, d.rowCount());
        p.setString(7, d.status().name()); p.setTimestamp(8, timestamp(d.createdAt()));
        p.setTimestamp(9, timestamp(d.updatedAt())); p.setTimestamp(10, timestamp(d.lastAnalyzedAt()));
        p.setBoolean(11, d.hasHeader()); p.setString(12, d.delimiter());
        if (id) p.setLong(13, d.id());
    }

    @Override public Optional<DatasetRecord> findDataset(long id) {
        try (Connection c = connection(); PreparedStatement p = c.prepareStatement("SELECT * FROM dataset WHERE id=?")) {
            p.setLong(1, id); try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(dataset(r)) : Optional.empty(); }
        } catch (SQLException e) { throw failure("读取 dataset", e); }
    }

    @Override public List<DatasetRecord> listDatasets() {
        try (Connection c = connection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM dataset ORDER BY id")) {
            List<DatasetRecord> values = new ArrayList<>(); while (r.next()) values.add(dataset(r)); return values;
        } catch (SQLException e) { throw failure("列出 dataset", e); }
    }

    @Override public void replaceMappings(long datasetId, List<FieldMapping> values) {
        String sql = "INSERT INTO dataset_field_mapping(dataset_id,source_field,source_index,standard_field,inferred_type,confidence,confirmed,samples_json) VALUES(?,?,?,?,?,?,?,?)";
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement d = c.prepareStatement("DELETE FROM dataset_field_mapping WHERE dataset_id=?")) { d.setLong(1, datasetId); d.executeUpdate(); }
            try (PreparedStatement p = c.prepareStatement(sql)) {
                for (FieldMapping v : values) {
                    p.setLong(1, datasetId); p.setString(2, v.sourceField()); p.setInt(3, v.sourceIndex());
                    p.setString(4, v.standardField() == null ? "" : v.standardField()); p.setString(5, v.inferredType());
                    p.setDouble(6, v.confidence()); p.setBoolean(7, v.confirmed());
                    p.setString(8, json.writeValueAsString(v.samples())); p.addBatch();
                }
                p.executeBatch(); c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        } catch (Exception e) { throw failure("保存字段映射", e); }
    }

    @Override public List<FieldMapping> findMappings(long datasetId) {
        String sql = "SELECT * FROM dataset_field_mapping WHERE dataset_id=? ORDER BY source_index";
        try (Connection c = connection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, datasetId); List<FieldMapping> values = new ArrayList<>();
            try (ResultSet r = p.executeQuery()) { while (r.next()) values.add(new FieldMapping(datasetId,
                    r.getString("source_field"), r.getInt("source_index"), emptyToNull(r.getString("standard_field")),
                    r.getString("inferred_type"), r.getDouble("confidence"), r.getBoolean("confirmed"),
                    json.readValue(r.getString("samples_json"), new TypeReference<List<String>>() {}))); }
            return values;
        } catch (Exception e) { throw failure("读取字段映射", e); }
    }

    @Override public AnalysisJob createJob(AnalysisJob j) {
        String sql = "INSERT INTO analysis_job(dataset_id,status,requested_modules,log_path,process_id,exit_code,error_message,created_at,started_at,finished_at) VALUES(?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = connection(); PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindJob(p, j, false); p.executeUpdate(); try (ResultSet r = p.getGeneratedKeys()) {
                if (!r.next()) throw new SQLException("未返回 job id");
                return new AnalysisJob(r.getLong(1), j.datasetId(), j.status(), j.requestedModules(), j.logPath(), j.processId(), j.exitCode(), j.errorMessage(), j.createdAt(), j.startedAt(), j.finishedAt());
            }
        } catch (SQLException e) { throw failure("创建 job", e); }
    }

    @Override public AnalysisJob updateJob(AnalysisJob j) {
        String sql = "UPDATE analysis_job SET dataset_id=?,status=?,requested_modules=?,log_path=?,process_id=?,exit_code=?,error_message=?,created_at=?,started_at=?,finished_at=? WHERE id=?";
        try (Connection c = connection(); PreparedStatement p = c.prepareStatement(sql)) {
            bindJob(p, j, true); if (p.executeUpdate() != 1) throw new IllegalArgumentException("job 不存在: " + j.id()); return j;
        } catch (SQLException e) { throw failure("更新 job", e); }
    }

    private static void bindJob(PreparedStatement p, AnalysisJob j, boolean id) throws SQLException {
        p.setLong(1, j.datasetId()); p.setString(2, j.status().name()); p.setString(3, j.requestedModules()); p.setString(4, j.logPath());
        nullableLong(p, 5, j.processId()); nullableInt(p, 6, j.exitCode()); p.setString(7, j.errorMessage());
        p.setTimestamp(8, timestamp(j.createdAt())); p.setTimestamp(9, timestamp(j.startedAt())); p.setTimestamp(10, timestamp(j.finishedAt()));
        if (id) p.setLong(11, j.id());
    }

    @Override public Optional<AnalysisJob> findJob(long id) {
        try (Connection c = connection(); PreparedStatement p = c.prepareStatement("SELECT * FROM analysis_job WHERE id=?")) {
            p.setLong(1, id); try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(job(r)) : Optional.empty(); }
        } catch (SQLException e) { throw failure("读取 job", e); }
    }

    @Override public List<AnalysisJob> listJobs() {
        try (Connection c = connection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM analysis_job ORDER BY id DESC")) {
            List<AnalysisJob> values = new ArrayList<>(); while (r.next()) values.add(job(r)); return values;
        } catch (SQLException e) { throw failure("列出 job", e); }
    }

    @Override public ResultVersion addResult(ResultVersion v) {
        String sql = "INSERT INTO result_version(dataset_id,job_id,module_name,result_path,created_at) VALUES(?,?,?,?,?)";
        try (Connection c = connection(); PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            p.setLong(1, v.datasetId()); p.setLong(2, v.jobId()); p.setString(3, v.module()); p.setString(4, v.resultPath()); p.setTimestamp(5, timestamp(v.createdAt()));
            p.executeUpdate(); try (ResultSet r = p.getGeneratedKeys()) { if (!r.next()) throw new SQLException("未返回 result id"); return new ResultVersion(r.getLong(1), v.datasetId(), v.jobId(), v.module(), v.resultPath(), v.createdAt()); }
        } catch (SQLException e) { throw failure("保存 result", e); }
    }

    @Override public List<ResultVersion> findResults(long datasetId, long jobId) {
        String sql = "SELECT * FROM result_version WHERE dataset_id=? AND job_id=? ORDER BY id";
        try (Connection c = connection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, datasetId); p.setLong(2, jobId); List<ResultVersion> values = new ArrayList<>();
            try (ResultSet r = p.executeQuery()) { while (r.next()) values.add(result(r)); } return values;
        } catch (SQLException e) { throw failure("读取 results", e); }
    }

    @Override public Optional<ResultVersion> findLatestSuccessfulResult(long datasetId, String module) {
        String sql = "SELECT r.* FROM result_version r JOIN analysis_job j ON j.id=r.job_id WHERE r.dataset_id=? AND r.module_name=? AND j.status='SUCCESS' ORDER BY r.id DESC LIMIT 1";
        try (Connection c = connection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, datasetId); p.setString(2, module); try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(result(r)) : Optional.empty(); }
        } catch (SQLException e) { throw failure("读取 latest result", e); }
    }

    private Connection connection() throws SQLException { return DriverManager.getConnection(url, user, password); }
    private static DatasetRecord dataset(ResultSet r) throws SQLException { return new DatasetRecord(r.getLong("id"), r.getString("name"), r.getString("description"), r.getString("dataset_type"), r.getString("file_path"), r.getLong("file_size"), r.getLong("row_count"), DatasetStatus.valueOf(r.getString("status")), instant(r.getTimestamp("created_at")), instant(r.getTimestamp("updated_at")), instant(r.getTimestamp("last_analyzed_at")), r.getBoolean("has_header"), r.getString("delimiter_char")); }
    private static AnalysisJob job(ResultSet r) throws SQLException {
        Number pid = (Number) r.getObject("process_id");
        Number code = (Number) r.getObject("exit_code");
        return new AnalysisJob(r.getLong("id"), r.getLong("dataset_id"),
                JobStatus.valueOf(r.getString("status")), r.getString("requested_modules"),
                r.getString("log_path"), pid == null ? null : pid.longValue(),
                code == null ? null : code.intValue(), r.getString("error_message"),
                instant(r.getTimestamp("created_at")), instant(r.getTimestamp("started_at")),
                instant(r.getTimestamp("finished_at")));
    }
    private static ResultVersion result(ResultSet r) throws SQLException { return new ResultVersion(r.getLong("id"), r.getLong("dataset_id"), r.getLong("job_id"), r.getString("module_name"), r.getString("result_path"), instant(r.getTimestamp("created_at"))); }
    private static Timestamp timestamp(String value) { return value == null ? null : Timestamp.from(Instant.parse(value)); }
    private static String instant(Timestamp value) { return value == null ? null : value.toInstant().toString(); }
    private static void nullableLong(PreparedStatement p, int i, Long value) throws SQLException { if (value == null) p.setNull(i, java.sql.Types.BIGINT); else p.setLong(i, value); }
    private static void nullableInt(PreparedStatement p, int i, Integer value) throws SQLException { if (value == null) p.setNull(i, java.sql.Types.INTEGER); else p.setInt(i, value); }
    private static String emptyToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private static RuntimeException failure(String action, Exception e) { return new IllegalStateException(action + "失败: " + e.getMessage(), e); }
}
