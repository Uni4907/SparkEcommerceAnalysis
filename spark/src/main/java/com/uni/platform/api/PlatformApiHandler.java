package com.uni.platform.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uni.platform.PlatformService;
import com.uni.platform.model.PlatformModels.AnalysisJob;
import com.uni.platform.model.PlatformModels.FieldMapping;
import com.uni.platform.model.PlatformModels.JobRequest;
import com.uni.platform.model.PlatformModels.RegistrationRequest;
import com.uni.platform.model.PlatformModels.ResultVersion;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlatformApiHandler implements HttpHandler {
    private static final int JSON_LIMIT = 1024 * 1024;
    private final PlatformService service;
    private final ObjectMapper json = new ObjectMapper();

    public PlatformApiHandler(PlatformService service) { this.service = service; }

    @Override public void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange x) throws Exception {
        String method = x.getRequestMethod().toUpperCase();
        String path = x.getRequestURI().getPath();
        List<String> parts = Arrays.stream(path.split("/")).filter(s -> !s.isBlank()).toList();

        if (path.equals("/api/health") && method.equals("GET")) {
            sendJson(x, 200, Map.of("status", "UP", "repository", service.config().repositoryMode())); return;
        }
        if (path.equals("/api/status") && method.equals("GET")) {
            sendJson(x, 200, systemStatus()); return;
        }
        if (path.equals("/api/datasets/upload") && method.equals("POST")) { upload(x); return; }
        if (path.equals("/api/datasets") && method.equals("GET")) { sendJson(x, 200, service.datasets().list()); return; }
        if (path.equals("/api/datasets") && method.equals("POST")) {
            sendJson(x, 201, service.datasets().register(readJson(x, RegistrationRequest.class))); return;
        }
        if (path.equals("/api/jobs") && method.equals("GET")) { sendJson(x, 200, service.jobs().list()); return; }
        if (path.equals("/api/jobs") && method.equals("POST")) {
            sendJson(x, 202, service.submit(readJson(x, JobRequest.class))); return;
        }

        if (parts.size() >= 3 && parts.get(0).equals("api") && parts.get(1).equals("datasets")) {
            long id = parseId(parts.get(2));
            if (parts.size() == 3 && method.equals("GET")) { sendJson(x, 200, service.datasets().get(id)); return; }
            if (parts.size() == 4 && parts.get(3).equals("mapping") && method.equals("GET")) {
                sendJson(x, 200, service.datasets().mappings(id)); return;
            }
            if (parts.size() == 4 && parts.get(3).equals("mapping") && method.equals("PUT")) {
                List<FieldMapping> mappings = readJson(x, new TypeReference<List<FieldMapping>>() {});
                sendJson(x, 200, service.datasets().confirmMappings(id, mappings)); return;
            }
            if (parts.size() == 5 && parts.get(3).equals("mapping") && parts.get(4).equals("recommend") && method.equals("POST")) {
                sendJson(x, 200, service.datasets().mappings(id)); return;
            }
            if (parts.size() == 4 && parts.get(3).equals("capabilities") && method.equals("GET")) {
                sendJson(x, 200, service.datasets().capabilities(id)); return;
            }
            if (parts.size() == 5 && parts.get(3).equals("analytics") && method.equals("GET")) {
                sendLatest(x, id, parts.get(4)); return;
            }
        }

        if (parts.size() >= 3 && parts.get(0).equals("api") && parts.get(1).equals("jobs")) {
            long id = parseId(parts.get(2));
            if (parts.size() == 3 && method.equals("GET")) { sendJson(x, 200, service.jobs().get(id)); return; }
            if (parts.size() == 4 && parts.get(3).equals("logs") && method.equals("GET")) {
                sendText(x, 200, "text/plain; charset=utf-8", service.jobs().readLog(id)); return;
            }
            if (parts.size() == 4 && parts.get(3).equals("results") && method.equals("GET")) {
                AnalysisJob job = service.jobs().get(id);
                sendJson(x, 200, service.repository().findResults(job.datasetId(), job.id())); return;
            }
        }
        sendJson(x, 404, Map.of("error", "API route not found"));
    }

    private Map<String, Object> systemStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<AnalysisJob> jobs = service.jobs().list();
        long activeJobs = jobs.stream().filter(job -> job.status().name().equals("PENDING")
                || job.status().name().equals("RUNNING")).count();
        Path warehouse = service.config().resultDir().resolve("spark-warehouse").toAbsolutePath().normalize();
        Path hiveDatabase = warehouse.resolve("ecommerce_v3.db");

        result.put("api", "UP");
        result.put("repositoryMode", service.config().repositoryMode());
        result.put("mysql", service.config().repositoryMode().equals("mysql") ? "UP" : "NOT_CONFIGURED");
        result.put("hive", Files.isDirectory(hiveDatabase) ? "READY" : "NOT_INITIALIZED");
        result.put("spark", activeJobs > 0 ? "RUNNING" : "IDLE");
        result.put("activeJobs", activeJobs);
        result.put("datasetCount", service.datasets().list().size());
        result.put("jobCount", jobs.size());
        result.put("warehouse", warehouse.toString());
        result.put("warehouseAvailable", Files.isDirectory(warehouse));
        result.put("bindHost", service.config().bindHost());
        result.put("port", service.config().port());
        return result;
    }

    private void upload(HttpExchange x) throws IOException {
        Map<String, String> query = query(x.getRequestURI().getRawQuery());
        String requested = query.get("filename");
        if (requested == null || requested.isBlank()) throw new IllegalArgumentException("filename 查询参数不能为空");
        String safeName = Path.of(requested).getFileName().toString();
        if (!safeName.toLowerCase().endsWith(".csv")) throw new IllegalArgumentException("仅支持 CSV 上传");
        long declared = parseLong(x.getRequestHeaders().getFirst("Content-Length"), -1);
        if (declared > service.config().uploadMaxBytes()) throw new IllegalArgumentException("上传文件超过限制");
        byte[] body = x.getRequestBody().readNBytes((int) Math.min(Integer.MAX_VALUE, service.config().uploadMaxBytes() + 1));
        if (body.length > service.config().uploadMaxBytes()) throw new IllegalArgumentException("上传文件超过限制");
        Path target = service.config().dataDir().resolve(UUID.randomUUID() + "-" + safeName).normalize();
        if (!target.startsWith(service.config().dataDir().normalize())) throw new IllegalArgumentException("非法文件名");
        Files.write(target, body, StandardOpenOption.CREATE_NEW);
        sendJson(x, 201, Map.of("filePath", target.toString(), "fileSize", body.length));
    }

    private void sendLatest(HttpExchange x, long datasetId, String module) throws IOException {
        ResultVersion version = service.latest(datasetId, module);
        Path path = Path.of(version.resultPath()).toAbsolutePath().normalize();
        Path root = service.config().resultDir().toAbsolutePath().normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new IllegalArgumentException("结果文件不可用");
        byte[] body = Files.readAllBytes(path);
        x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        x.sendResponseHeaders(200, body.length); x.getResponseBody().write(body);
    }

    private <T> T readJson(HttpExchange x, Class<T> type) throws IOException {
        byte[] body = readBounded(x); return json.readValue(body, type);
    }
    private <T> T readJson(HttpExchange x, TypeReference<T> type) throws IOException {
        byte[] body = readBounded(x); return json.readValue(body, type);
    }
    private static byte[] readBounded(HttpExchange x) throws IOException {
        byte[] body = x.getRequestBody().readNBytes(JSON_LIMIT + 1);
        if (body.length > JSON_LIMIT) throw new IllegalArgumentException("JSON 请求体过大");
        return body;
    }

    private void sendJson(HttpExchange x, int status, Object value) throws IOException {
        byte[] body = json.writeValueAsBytes(value);
        x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(status, body.length); x.getResponseBody().write(body);
    }
    private static void sendText(HttpExchange x, int status, String contentType, String value) throws IOException {
        byte[] body = value.getBytes(StandardCharsets.UTF_8); x.getResponseHeaders().set("Content-Type", contentType);
        x.sendResponseHeaders(status, body.length); x.getResponseBody().write(body);
    }
    private static long parseId(String value) { try { return Long.parseLong(value); } catch (NumberFormatException e) { throw new IllegalArgumentException("ID 无效: " + value); } }
    private static long parseLong(String value, long fallback) { try { return value == null ? fallback : Long.parseLong(value); } catch (NumberFormatException e) { return fallback; } }
    private static Map<String, String> query(String raw) {
        Map<String, String> result = new LinkedHashMap<>(); if (raw == null) return result;
        for (String pair : raw.split("&")) { String[] p = pair.split("=", 2); result.put(URLDecoder.decode(p[0], StandardCharsets.UTF_8), p.length == 2 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8) : ""); }
        return result;
    }
}
