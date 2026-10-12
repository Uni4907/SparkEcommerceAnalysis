package com.uni.platform.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Configuration is explicit: memory and MySQL modes are never silently interchanged. */
public record PlatformConfig(
        String bindHost,
        int port,
        Path dataDir,
        Path resultDir,
        Path jobDir,
        String repositoryMode,
        String jdbcUrl,
        String jdbcUser,
        String jdbcPassword,
        String mavenCommand,
        Path projectDir,
        long uploadMaxBytes) {

    public static PlatformConfig load() {
        Path project = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path runtime = Paths.get(System.getProperty("platform.runtime.dir",
                project.resolve("runtime").toString())).toAbsolutePath().normalize();
        return new PlatformConfig(
                System.getProperty("platform.bind.host", "127.0.0.1"),
                Integer.getInteger("spark.dashboard.port", 8090),
                Paths.get(System.getProperty("platform.data.dir", runtime.resolve("datasets").toString())),
                Paths.get(System.getProperty("platform.result.dir", runtime.resolve("results").toString())),
                Paths.get(System.getProperty("platform.job.dir", runtime.resolve("jobs").toString())),
                setting("platform.repository", "PLATFORM_REPOSITORY", "memory").toLowerCase(),
                setting("platform.jdbc.url", "PLATFORM_JDBC_URL", ""),
                setting("platform.jdbc.user", "PLATFORM_JDBC_USER", ""),
                setting("platform.jdbc.password", "PLATFORM_JDBC_PASSWORD", ""),
                System.getProperty("platform.maven.command", isWindows() ? "mvn.cmd" : "mvn"),
                project,
                Long.getLong("platform.upload.max.bytes", 512L * 1024L * 1024L));
    }

    private static String setting(String property, String environment, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) value = System.getenv(environment);
        return value == null || value.isBlank() ? fallback : value;
    }

    public void validate() {
        if (!repositoryMode.equals("memory") && !repositoryMode.equals("mysql")) {
            throw new IllegalArgumentException("platform.repository 必须为 memory 或 mysql");
        }
        if (repositoryMode.equals("mysql") && jdbcUrl.isBlank()) {
            throw new IllegalStateException("MySQL 模式必须设置 -Dplatform.jdbc.url");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("端口无效: " + port);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
