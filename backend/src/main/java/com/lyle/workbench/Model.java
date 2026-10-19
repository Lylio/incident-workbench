package com.lyle.workbench;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class Model {
  private Model() {}

  public enum Status {
    OPEN,
    RESOLVED
  }

  public enum Decision {
    ACCEPTED,
    REJECTED
  }

  public record Evidence(
      String id,
      Instant timestamp,
      String service,
      String level,
      String traceId,
      String message,
      String fileName,
      int lineNumber,
      String raw) {}

  public record Source(
      String name, int totalLines, int parsedLines, int rejectedLines, String content) {}

  public record Diagnostic(String fileName, int lineNumber, String reason) {}

  public record Incident(
      UUID id,
      Instant createdAt,
      String title,
      List<Evidence> entries,
      List<Source> sources,
      List<Diagnostic> diagnostics,
      int rejectedLines) {}

  public record Summary(
      UUID id,
      Instant createdAt,
      String title,
      Status status,
      int entries,
      int errors,
      int services,
      int rejectedLines) {}

  public record Hypothesis(String title, String reasoning, List<String> evidenceIds) {}

  public record Draft(
      String summary,
      List<Hypothesis> hypotheses,
      List<String> nextChecks,
      List<String> limitations) {}

  public record Context(
      List<Evidence> entries, int totalEntries, String selection, int characterCount) {}

  public record Analysis(
      UUID id, UUID incidentId, Instant createdAt, String model, Draft draft, Context context) {}

  public record Review(
      UUID id,
      UUID incidentId,
      UUID analysisId,
      int hypothesisIndex,
      Decision status,
      String note,
      Instant createdAt) {}

  public record Investigation(
      Incident incident, Status status, List<Analysis> analyses, List<Review> reviews) {}

  public record ContextRequest(
      @NotNull @Size(max = 60) List<@NotBlank @Size(max = 10) String> evidenceIds) {}

  public record AnalyzeRequest(
      @NotNull @Size(max = 60) List<@NotBlank @Size(max = 10) String> evidenceIds,
      @NotNull @Size(max = 1000) String question) {}

  public record ReviewRequest(
      @NotNull UUID analysisId,
      @Min(0) @Max(2) int hypothesisIndex,
      @NotNull Decision status,
      @NotNull @Size(max = 2000) String note) {}

  public record StatusRequest(@NotNull Status status) {}

  public record Upload(String name, byte[] bytes) {}
}
