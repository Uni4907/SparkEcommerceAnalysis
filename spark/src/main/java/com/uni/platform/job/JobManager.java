package com.uni.platform.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uni.platform.config.PlatformConfig;
import com.uni.platform.model.PlatformModels.AnalysisJob;
import com.uni.platform.model.PlatformModels.DatasetRecord;
import com.uni.platform.model.PlatformModels.DatasetStatus;
import com.uni.platform.model.PlatformModels.JobStatus;
import com.uni.platform.model.PlatformModels.ResultVersion;
import com.uni.platform.model.PlatformModels.WorkerManifest;
import com.uni.platform.model.PlatformModels.WorkerSpec;
import com.uni.platform.persistence.PlatformRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Creates durable job state, launches Spark as a subprocess, and captures its log. */
public class JobManager implements AutoCloseable {
    private final PlatformRepository repository;
    private final PlatformConfig config;
    private final ObjectMapper json = new ObjectMapper();
    private final ExecutorService monitor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "analysis-job-monitor"); t.setDaemon(true); return t;
    });

    public JobManager(PlatformRepository repository, PlatformConfig config) {
        this.repository = repository;
        this.config = config;
        recoverInterruptedJobs();
    }

    public AnalysisJob submit(DatasetRecord dataset, List<com.uni.platform.model.PlatformModels.FieldMapping> mappings,
                              List<String> modules, boolean hiveEnabled) throws IOException {
        if (dataset.status() != DatasetStatus.READY) {
            throw new IllegalStateException("dataset 必须先确认字段映射: " + dataset.id());
        }
        String now = Instant.now().toString();
        AnalysisJob created = repository.createJob(new AnalysisJob(0, dataset.id(), JobStatus.PENDING,
                String.join(",", modules), "", null, null, null, now, null, null));
        Path dir = config.jobDir().resolve(Long.toString(created.id()));
        Files.createDirectories(dir);
        Path log = dir.resolve("job.log");
        Path specPath = dir.resolve("job-spec.json");
        AnalysisJob pending = new AnalysisJob(created.id(), created.datasetId(), created.status(),
                created.requestedModules(), log.toString(), null, null, null, created.createdAt(), null, null);
        repository.updateJob(pending);
        json.writerWithDefaultPrettyPrinter().writeValue(specPath.toFile(),
                new WorkerSpec(dataset, mappings, modules, config.resultDir().toString(), hiveEnabled));
        monitor.submit(() -> runProcess(pending, specPath, log));
        return pending;
    }

    public AnalysisJob get(long id) {
        return repository.findJob(id).orElseThrow(() -> new IllegalArgumentException("job 不存在: " + id));
    }
    public List<AnalysisJob> list() { return repository.listJobs(); }
    public String readLog(long id) throws IOException {
        Path path = Path.of(get(id).logPath());
        return Files.exists(path) ? new String(Files.readAllBytes(path), Charset.defaultCharset()) : "";
    }

    private void runProcess(AnalysisJob pending, Path spec, Path log) {
        AnalysisJob running = pending;
        try {
            Files.createDirectories(config.resultDir());
            List<String> command = new ArrayList<>();
            command.add(config.mavenCommand());
            command.add("-q");
            String mavenRepo = System.getProperty("maven.repo.local");
            if (mavenRepo != null && !mavenRepo.isBlank()) {
                command.add("-Dmaven.repo.local=" + mavenRepo);
            }
            command.add("-DskipTests");
            command.add("-Dplatform.job.spec=" + spec.toAbsolutePath());
            command.add("-Dplatform.job.id=" + pending.id());
            command.add("exec:java@worker");
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(config.projectDir().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile());
            String javaHome = System.getProperty("java.home");
            builder.environment().put("JAVA_HOME", javaHome);
            String pathKey = builder.environment().keySet().stream()
                    .filter(k -> k.equalsIgnoreCase("PATH")).findFirst().orElse("PATH");
            builder.environment().put(pathKey, Path.of(javaHome, "bin") + ";" + builder.environment().getOrDefault(pathKey, ""));
            Process process = builder.start();
            running = pending.running(process.pid(), Instant.now().toString());
            repository.updateJob(running);
            repository.updateDataset(repository.findDataset(pending.datasetId()).orElseThrow()
                    .withStatus(DatasetStatus.ANALYZING, Instant.now().toString()));
            int code = process.waitFor();
            if (code == 0) {
                WorkerManifest manifest = registerManifest(pending.datasetId(), pending.id());
                repository.updateJob(running.finished(JobStatus.SUCCESS, code, null, Instant.now().toString()));
                repository.updateDataset(repository.findDataset(pending.datasetId()).orElseThrow()
                        .analyzed(Instant.now().toString()).withRowCount(manifest.rowCount()));
            } else {
                fail(running, code, "Spark 子进程退出码 " + code);
            }
        } catch (Exception e) {
            fail(running, -1, e.getMessage());
        }
    }

    private WorkerManifest registerManifest(long datasetId, long jobId) throws IOException {
        Path path = config.resultDir().resolve(Long.toString(datasetId)).resolve(Long.toString(jobId)).resolve("manifest.json");
        WorkerManifest manifest = json.readValue(path.toFile(), WorkerManifest.class);
        for (Map.Entry<String, String> entry : manifest.resultFiles().entrySet()) {
            repository.addResult(new ResultVersion(0, datasetId, jobId, entry.getKey(), entry.getValue(), manifest.completedAt()));
        }
        return manifest;
    }

    private void fail(AnalysisJob job, int code, String message) {
        try {
            repository.updateJob(job.finished(JobStatus.FAILED, code, message, Instant.now().toString()));
            repository.findDataset(job.datasetId()).ifPresent(dataset -> repository.updateDataset(
                    dataset.withStatus(DatasetStatus.READY, Instant.now().toString())));
        } catch (Exception ignored) { }
    }

    private void recoverInterruptedJobs() {
        for (AnalysisJob job : repository.listJobs()) {
            if (job.status() == JobStatus.PENDING || job.status() == JobStatus.RUNNING) {
                repository.updateJob(job.finished(JobStatus.FAILED, -1,
                        "服务重启时检测到未完成任务", Instant.now().toString()));
            }
        }
    }

    @Override public void close() { monitor.shutdownNow(); }
}
