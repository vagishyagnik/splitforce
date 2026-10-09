package com.splitforce.seed;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

@Component
public class CsvExpenseParser {

    public record ParsedRow(
            LocalDate date,
            String description,
            String category,
            BigDecimal cost,
            String currency,
            Map<String, BigDecimal> memberNet) {}

    public record ParsedFile(List<String> memberHeaders, List<ParsedRow> rows) {}

    public ParsedFile parse(InputStream in) throws IOException, CsvException {
        try (CSVReader reader = new CSVReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String[]> all = reader.readAll();

            String[] headerRow = null;
            for (String[] r : all) {
                if (r.length > 0 && "Date".equalsIgnoreCase(r[0].trim())) {
                    headerRow = r;
                    break;
                }
            }
            if (headerRow == null) throw new IOException("No 'Date' header row found in CSV");

            List<String> memberHeaders = new ArrayList<>();
            for (int i = 5; i < headerRow.length; i++) memberHeaders.add(headerRow[i].trim());

            List<ParsedRow> rows = new ArrayList<>();
            boolean headerSeen = false;
            for (String[] r : all) {
                if (!headerSeen) {
                    headerSeen = r.length > 0 && "Date".equalsIgnoreCase(r[0].trim());
                    continue;
                }
                if (r.length < 5) continue;
                String date = r[0].trim();
                String desc = r[1].trim();
                if (date.isEmpty()) continue;
                if ("Total balance".equalsIgnoreCase(desc)) continue;
                if (r[3] == null || r[3].isBlank()) continue;

                LocalDate d = LocalDate.parse(date);
                BigDecimal cost = new BigDecimal(r[3].trim());
                String cat = r[2].trim();
                String cur = r[4].trim();
                Map<String, BigDecimal> net = new LinkedHashMap<>();
                for (int i = 5; i < r.length && i - 5 < memberHeaders.size(); i++) {
                    String v = r[i].trim();
                    if (v.isEmpty()) continue;
                    net.put(memberHeaders.get(i - 5), new BigDecimal(v));
                }
                rows.add(new ParsedRow(d, desc, cat, cost, cur, net));
            }
            return new ParsedFile(memberHeaders, rows);
        }
    }
}
