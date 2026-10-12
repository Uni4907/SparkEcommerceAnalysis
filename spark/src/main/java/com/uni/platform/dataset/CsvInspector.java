package com.uni.platform.dataset;

import com.uni.platform.model.PlatformModels.FieldProfile;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Bounded CSV inspection: field recommendation never scans a large file in full. */
public class CsvInspector {
    public record Inspection(boolean hasHeader, String delimiter, List<FieldProfile> fields, long rowCount) {}

    public Inspection inspect(Path path, Boolean requestedHeader, String requestedDelimiter) throws IOException {
        String delimiter = requestedDelimiter == null || requestedDelimiter.isEmpty() ? "," : requestedDelimiter;
        if (delimiter.length() != 1) throw new IllegalArgumentException("当前仅支持单字符分隔符");

        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while (rows.size() < 51 && (line = reader.readLine()) != null) {
                if (!line.isBlank()) rows.add(parseLine(line, delimiter.charAt(0)));
            }
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV 为空: " + path);

        boolean header = requestedHeader != null ? requestedHeader : looksLikeHeader(rows);
        String[] first = rows.get(0);
        int width = first.length;
        List<FieldProfile> fields = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            String name = header ? first[i].strip() : "_c" + i;
            List<String> samples = new ArrayList<>();
            int start = header ? 1 : 0;
            for (int r = start; r < rows.size() && samples.size() < 5; r++) {
                if (i < rows.get(r).length && !rows.get(r)[i].isBlank()) samples.add(rows.get(r)[i]);
            }
            fields.add(new FieldProfile(name, i, inferType(samples), List.copyOf(samples)));
        }

        long rowCount = -1;
        if (Files.size(path) <= 10L * 1024L * 1024L) {
            try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
                rowCount = lines.filter(line -> !line.isBlank()).count() - (header ? 1 : 0);
            }
        }
        return new Inspection(header, delimiter, List.copyOf(fields), Math.max(rowCount, -1));
    }

    static String[] parseLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (ch == delimiter && !quoted) {
                values.add(current.toString().strip());
                current.setLength(0);
            } else current.append(ch);
        }
        values.add(current.toString().strip());
        return values.toArray(String[]::new);
    }

    private static boolean looksLikeHeader(List<String[]> rows) {
        if (rows.size() < 2) return false;
        List<String> known = Arrays.asList("user_id", "userid", "item_id", "product_id",
                "category_id", "event_type", "behavior", "timestamp", "event_time",
                "order_id", "order_time", "quantity", "qty", "unit_price", "price", "amount");
        int aliases = 0;
        for (String value : rows.get(0)) {
            String normalized = value.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
            if (known.contains(normalized)) aliases++;
        }
        return aliases >= Math.max(1, rows.get(0).length / 3);
    }

    private static String inferType(List<String> samples) {
        if (samples.isEmpty()) return "string";
        boolean integer = true, decimal = true, bool = true, timestamp = true;
        for (String value : samples) {
            integer &= value.matches("[-+]?\\d+");
            decimal &= value.matches("[-+]?(?:\\d+\\.?\\d*|\\d*\\.\\d+)");
            bool &= value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
            timestamp &= value.matches("\\d{4}-\\d{1,2}-\\d{1,2}([ T].*)?") || value.matches("\\d{10,13}");
        }
        if (bool) return "boolean";
        if (timestamp && !integer) return "timestamp";
        if (integer) return "long";
        if (decimal) return "double";
        return "string";
    }
}
