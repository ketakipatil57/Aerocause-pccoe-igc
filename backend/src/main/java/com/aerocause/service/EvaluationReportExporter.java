package com.aerocause.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class EvaluationReportExporter {
    private final ObjectMapper objectMapper;
    private final Path exportDirectory;

    public EvaluationReportExporter(
            ObjectMapper objectMapper,
            @Value("${aerocause.evaluation.export-dir:../docs}") String exportDirectory) {
        this.objectMapper = objectMapper;
        this.exportDirectory = Path.of(exportDirectory);
    }

    public void export(String resultsJson) {
        try {
            JsonNode results = objectMapper.readTree(resultsJson);
            Files.createDirectories(exportDirectory);
            Files.writeString(exportDirectory.resolve("evaluation-results.csv"),
                    toCsv(results), StandardCharsets.UTF_8);
            Files.writeString(exportDirectory.resolve("evaluation-results.md"),
                    toMarkdown(results), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not export evaluation reports", e);
        }
    }

    private String toCsv(JsonNode results) {
        StringBuilder csv = new StringBuilder(
                "episode_id,name,data_type,expected,actual,fault_expected,fault_flagged," +
                "first_attempt_validator_violations,final_unsupported_claims,citations_valid," +
                "time_to_brief_ms,build_pass\n");
        for (JsonNode episode : results.path("episodes")) {
            appendCsvRow(csv, Arrays.asList(
                    text(episode, "episodeId"), text(episode, "name"),
                    text(episode, "synthetic").equals("true") ? "synthetic" : "real",
                    text(episode, "expected"), text(episode, "actual"),
                    text(episode, "expectedFault"), text(episode, "sensorFaultFlagged"),
                    text(episode, "firstAttemptValidatorViolations"),
                    text(episode, "unsupportedClaimsFinal"), text(episode, "citationsValid"),
                    text(episode, "timeToBriefMs"), text(episode, "buildPass")
            ));
        }
        return csv.toString();
    }

    private String toMarkdown(JsonNode results) {
        StringBuilder markdown = new StringBuilder("# Evaluation Results\n\n")
                .append("- Run: ").append(text(results, "runAt")).append('\n')
                .append("- Build gate: ").append(Boolean.parseBoolean(text(results, "buildPass")) ? "PASS" : "FAIL")
                .append("\n- Unsupported claims in final briefs: ")
                .append(text(results, "unsupportedClaimsFinalTotal"))
                .append("\n\n> Synthetic episodes are pipeline fixtures, not an accuracy claim. ")
                .append("Real episodes have no expected labels unless supplied by a human.\n\n")
                .append("| Type | Episode | Expected | Actual | Fault flagged | First-attempt violations | ")
                .append("Final unsupported claims | Citations valid | Time to brief (ms) | Build |\n")
                .append("|---|---|---|---|---:|---:|---:|---|---:|---|\n");
        for (JsonNode episode : results.path("episodes")) {
            markdown.append("| ")
                    .append(text(episode, "synthetic").equals("true") ? "Synthetic" : "Real")
                    .append(" | ").append(escapeMarkdown(text(episode, "name")))
                    .append(" | ").append(display(text(episode, "expected")))
                    .append(" | ").append(display(text(episode, "actual")))
                    .append(" | ").append(display(text(episode, "sensorFaultFlagged")))
                    .append(" | ").append(display(text(episode, "firstAttemptValidatorViolations")))
                    .append(" | ").append(display(text(episode, "unsupportedClaimsFinal")))
                    .append(" | ").append(display(text(episode, "citationsValid")))
                    .append(" | ").append(display(text(episode, "timeToBriefMs")))
                    .append(" | ").append(Boolean.parseBoolean(text(episode, "buildPass")) ? "PASS" : "FAIL")
                    .append(" |\n");
        }
        return markdown.toString();
    }

    private static void appendCsvRow(StringBuilder csv, List<String> values) {
        List<String> escaped = new ArrayList<>(values.size());
        for (String value : values) {
            escaped.add("\"" + value.replace("\"", "\"\"") + "\"");
        }
        csv.append(String.join(",", escaped)).append('\n');
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static String display(String value) {
        return value.isBlank() ? "—" : value;
    }

    private static String escapeMarkdown(String value) {
        return value.replace("|", "\\|").replace("\n", " ");
    }
}
