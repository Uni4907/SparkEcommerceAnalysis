package com.uni.util;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 分析结果导出工具。
 *
 * Spark DataFrame.write() 默认会生成 part-*.csv 分片文件，
 * 本工具将分片文件合并重命名为 output/data/<名称>.csv，
 * 并额外生成前端可视化可直接读取的 <名称>.json 数组文件。
 */
public final class ResultExporter {

    private ResultExporter() {
    }

    /** 导出为 CSV + JSON（结果行数较小时使用，例如各类汇总统计表） */
    public static void exportTable(Dataset<Row> ds, Path outputDir, String name) {
        export(ds, outputDir, name, true, 5000);
    }

    /** 只导出 CSV（结果行数较大时使用，例如用户画像宽表） */
    public static void exportCsv(Dataset<Row> ds, Path outputDir, String name) {
        export(ds, outputDir, name, false, 0);
    }

    /**
     * 手动构造的小汇总表导出（CSV + JSON）。
     * 用于"数据总量/用户数"等来自多个 DataFrame 统计结果拼装的概要数据。
     *
     * @param rows 每行是一组列值，取值可以是 Long / Integer / Double / String
     */
    public static void manualTable(Path outputDir, String name,
                                   String[] columns, List<Object[]> rows) {
        try {
            Path dataDir = outputDir.resolve("data");
            Files.createDirectories(dataDir);

            // CSV
            StringBuilder csv = new StringBuilder();
            csv.append(String.join(",", columns)).append("\r\n");
            for (Object[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) {
                        csv.append(",");
                    }
                    csv.append(toCsvCell(row[i]));
                }
                csv.append("\r\n");
            }
            Files.write(dataDir.resolve(name + ".csv"),
                    csv.toString().getBytes(StandardCharsets.UTF_8));

            // JSON
            StringBuilder json = new StringBuilder("[");
            for (int r = 0; r < rows.size(); r++) {
                if (r > 0) {
                    json.append(",");
                }
                Object[] row = rows.get(r);
                json.append("{");
                for (int c = 0; c < row.length; c++) {
                    if (c > 0) {
                        json.append(",");
                    }
                    json.append("\"").append(columns[c]).append("\":");
                    appendValue(json, row[c]);
                }
                json.append("}");
            }
            json.append("]");
            Files.write(dataDir.resolve(name + ".json"),
                    json.toString().getBytes(StandardCharsets.UTF_8));

            System.out.println("[导出] " + dataDir.resolve(name + ".csv"));
        } catch (IOException e) {
            throw new RuntimeException("导出失败: " + name, e);
        }
    }

    private static String toCsvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static void export(Dataset<Row> ds, Path outputDir, String name,
                               boolean withJson, int jsonMaxRows) {
        try {
            Path dataDir = outputDir.resolve("data");
            Files.createDirectories(dataDir);

            // 1) CSV：先写入临时目录，再合并为单个文件
            Path tmpDir = dataDir.resolve(".tmp_" + name);
            deleteRecursively(tmpDir);
            ds.coalesce(1)
                    .write()
                    .mode("overwrite")
                    .option("header", "true")
                    .csv(tmpDir.toString());

            Path targetCsv = dataDir.resolve(name + ".csv");
            Path partFile = findPartFile(tmpDir, ".csv");
            Files.move(partFile, targetCsv, StandardCopyOption.REPLACE_EXISTING);
            deleteRecursively(tmpDir);

            // 2) JSON：小结果集转为 JSON 数组，供可视化页面 fetch 使用
            if (withJson) {
                List<Row> rows = ds.collectAsList();
                if (rows.size() > jsonMaxRows) {
                    rows = new ArrayList<>(rows.subList(0, jsonMaxRows));
                }
                String[] columns = ds.columns();
                String json = toJsonArray(rows, columns);
                Files.write(dataDir.resolve(name + ".json"),
                        json.getBytes(StandardCharsets.UTF_8));
            }

            System.out.println("[导出] " + dataDir.resolve(name + ".csv"));
        } catch (Exception e) {
            throw new RuntimeException("导出结果失败: " + name, e);
        }
    }

    /** 在 Spark 写入的临时目录中找到唯一的 part 文件 */
    private static Path findPartFile(Path dir, String suffix) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().startsWith("part-"))
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .findFirst()
                    .orElseThrow(() -> new IOException("未找到 part 文件: " + dir));
        }
    }

    /** 递归删除临时目录（仅作用于 output/data 下我们创建的 .tmp_ 目录） */
    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    /** 将 Row 集合转换为 JSON 数组字符串 */
    private static String toJsonArray(List<Row> rows, String[] columns) {
        StringBuilder sb = new StringBuilder("[");
        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) {
                sb.append(",");
            }
            sb.append("{");
            Row row = rows.get(r);
            for (int c = 0; c < columns.length; c++) {
                if (c > 0) {
                    sb.append(",");
                }
                sb.append("\"").append(columns[c]).append("\":");
                appendValue(sb, row.get(c));
            }
            sb.append("}");
        }
        return sb.append("]").toString();
    }

    private static void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number) {
            Number number = (Number) value;
            double d = number.doubleValue();
            if (Double.isInfinite(d) || Double.isNaN(d)) {
                sb.append("null");
            } else if (value instanceof Double || value instanceof Float) {
                // 保留最多 6 位小数，去掉多余的 0
                String text = String.format("%.6f", d);
                while (text.endsWith("0")) {
                    text = text.substring(0, text.length() - 1);
                }
                if (text.endsWith(".")) {
                    text = text.substring(0, text.length() - 1);
                }
                sb.append(text);
            } else {
                sb.append(number.longValue());
            }
        } else {
            sb.append("\"").append(escapeJson(value.toString())).append("\"");
        }
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(ch);
            }
        }
        return sb.toString();
    }
}
