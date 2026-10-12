package com.uni.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 前端静态资源复制工具。
 *
 * 页面资源（index.html / app.js / style.css / echarts.min.js）
 * 存放在 src/main/resources/web 下，随 jar/classes 打入 classpath，
 * 每次分析完成后复制到 output 目录，与导出的 data/*.json 一起
 * 由 DashboardServer 通过 HTTP 提供服务。
 */
public final class WebAssets {

    private static final String[] FILES = {
            "index.html",
            "app.js",
            "style.css",
            "echarts.min.js",
            "platform.html",
            "platform.js",
            "platform.css"
    };

    private WebAssets() {
    }

    public static void copyTo(Path outputDir) {
        try {
            for (String file : FILES) {
                Path target = outputDir.resolve(file);
                try (InputStream in = WebAssets.class.getResourceAsStream("/web/" + file)) {
                    if (in == null) {
                        throw new IOException("classpath 中缺少静态资源: /web/" + file);
                    }
                    Files.createDirectories(outputDir);
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            System.out.println("[可视化] 静态页面已复制到 " + outputDir.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("复制可视化静态资源失败", e);
        }
    }
}
