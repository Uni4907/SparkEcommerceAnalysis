package com.uni.dashboard;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.uni.platform.PlatformService;
import com.uni.platform.api.PlatformApiHandler;
import com.uni.platform.config.PlatformConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.InputStream;

/**
 * 可视化结果 HTTP 服务。
 *
 * 使用 JDK 自带的 com.sun.net.httpserver，无需引入 Spring Boot 等框架：
 *  - 根路径 "/"          → output 目录下 index.html（静态页面）
 *  - /app.js /style.css  → 前端脚本与样式
 *  - /data/*.json        → Spark 导出的分析结果 JSON
 *
 * 启动方式（默认端口 8090）：
 *  1) 先运行 Main 完成分析，确保 output/data 下已有结果文件；
 *  2) 运行本类 main，或使用 -Dspark.dashboard.port=xxxx 修改端口；
 *  3) 浏览器访问 http://127.0.0.1:8090/index.html
 */
public class DashboardServer {

    public static void main(String[] args) throws IOException {
        // 输出目录：优先使用第一个参数，否则使用系统属性，最后回退到当前目录/output
        String outputDirText = args.length > 0
                ? args[0]
                : System.getProperty("spark.output.dir",
                Paths.get(System.getProperty("user.dir"), "output").toString());
        PlatformConfig platformConfig = PlatformConfig.load();
        int port = platformConfig.port();

        Path outputDir = Paths.get(outputDirText).toAbsolutePath().normalize();
        if (!Files.exists(outputDir)) {
            throw new IllegalStateException("输出目录不存在: " + outputDir
                    + "，请先运行 com.uni.Main 完成分析");
        }

        PlatformService platform = new PlatformService(platformConfig);
        HttpServer server = HttpServer.create(new InetSocketAddress(platformConfig.bindHost(), port), 0);
        server.createContext("/api/", new PlatformApiHandler(platform));
        server.createContext("/", new StaticFileHandler(outputDir));
        server.setExecutor(null);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(platform::close));
        System.out.println("V3 平台: http://" + platformConfig.bindHost() + ":" + port + "/platform.html");
        System.out.println("Legacy 页面: http://" + platformConfig.bindHost() + ":" + port + "/index.html");
        System.out.println("输出目录: " + outputDir);
    }

    /** 将 URL 路径安全地映射到输出目录下的静态文件 */
    private static class StaticFileHandler implements HttpHandler {

        private final Path root;

        StaticFileHandler(Path root) {
            this.root = root;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/")) {
                path = "/platform.html";
            }

            // 路径穿越防护：规范化后必须仍位于输出目录内
            Path file = root.resolve("." + path).normalize();
            if (!file.startsWith(root)) {
                send(exchange, 404, "text/plain; charset=utf-8",
                        "Not Found: " + path);
                return;
            }

            if (!Files.isRegularFile(file)) {
                try (InputStream in = DashboardServer.class.getResourceAsStream("/web" + path)) {
                    if (in == null) {
                        send(exchange, 404, "text/plain; charset=utf-8", "Not Found: " + path);
                        return;
                    }
                    send(exchange, 200, contentType(file.getFileName().toString()), in.readAllBytes());
                    return;
                }
            }

            byte[] body = Files.readAllBytes(file);
            send(exchange, 200, contentType(file.getFileName().toString()), body);
        }

        private static void send(HttpExchange exchange, int code,
                                 String contentType, String text) throws IOException {
            send(exchange, code, contentType, text.getBytes(StandardCharsets.UTF_8));
        }

        private static void send(HttpExchange exchange, int code,
                                 String contentType, byte[] body) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(code, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
            exchange.close();
        }

        private static String contentType(String fileName) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".html")) {
                return "text/html; charset=utf-8";
            }
            if (lower.endsWith(".js")) {
                return "application/javascript; charset=utf-8";
            }
            if (lower.endsWith(".css")) {
                return "text/css; charset=utf-8";
            }
            if (lower.endsWith(".json")) {
                return "application/json; charset=utf-8";
            }
            if (lower.endsWith(".png")) {
                return "image/png";
            }
            if (lower.endsWith(".svg")) {
                return "image/svg+xml";
            }
            return "application/octet-stream";
        }
    }
}
