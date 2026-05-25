package com.javadb.engine;

import com.javadb.types.Value;
import com.javadb.types.Values;

import java.util.ArrayList;
import java.util.List;

public record QueryResult(List<String> columns, List<List<Value>> rows, String message) {

    public static QueryResult of(List<String> columns, List<List<Value>> rows) {
        return new QueryResult(columns, rows, null);
    }

    public static QueryResult message(String message) {
        return new QueryResult(List.of(), List.of(), message);
    }

    public boolean isQuery() {
        return message == null;
    }

    public String format() {
        if (!isQuery()) {
            return message;
        }
        int columnCount = columns.size();
        int[] widths = new int[columnCount];
        for (int i = 0; i < columnCount; i++) {
            widths[i] = columns.get(i).length();
        }
        List<List<String>> text = new ArrayList<>();
        for (List<Value> row : rows) {
            List<String> line = new ArrayList<>();
            for (int i = 0; i < columnCount; i++) {
                String cell = Values.render(row.get(i));
                line.add(cell);
                widths[i] = Math.max(widths[i], cell.length());
            }
            text.add(line);
        }
        StringBuilder sb = new StringBuilder();
        appendRow(sb, columns, widths);
        appendSeparator(sb, widths);
        for (List<String> line : text) {
            appendRow(sb, line, widths);
        }
        sb.append('(').append(rows.size()).append(rows.size() == 1 ? " row)" : " rows)");
        return sb.toString();
    }

    private void appendRow(StringBuilder sb, List<String> cells, int[] widths) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(pad(cells.get(i), widths[i]));
        }
        sb.append('\n');
    }

    private void appendSeparator(StringBuilder sb, int[] widths) {
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) {
                sb.append("-+-");
            }
            sb.append("-".repeat(widths[i]));
        }
        sb.append('\n');
    }

    private String pad(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }
}
