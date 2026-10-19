package com.lyle.workbench;

import static com.lyle.workbench.Model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InvestigationService {
  private final WorkbenchStore store;
  private final EvidenceContext context;
  private final Analyst analyst;
  private final Semaphore admission = new Semaphore(1);

  public InvestigationService(WorkbenchStore s, EvidenceContext c, Analyst a) {
    store = s;
    context = c;
    analyst = a;
  }

  public Analysis analyze(UUID id, AnalyzeRequest request) {
    Incident incident = store.incident(id);
    Context selected = context.select(incident, request.evidenceIds());
    if (!analyst.available())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "AI is not configured. Add OPENAI_API_KEY locally and restart the backend. Evidence tools"
              + " work without a key.");
    if (!admission.tryAcquire())
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "An AI analysis is already running. Try again shortly.");
    try {
      String prompt =
          store.encode(
              Map.of(
                  "incidentTitle",
                  incident.title(),
                  "question",
                  request.question(),
                  "contextSelection",
                  selected.selection(),
                  "totalEvidenceRecords",
                  selected.totalEntries(),
                  "providedEvidence",
                  selected.entries()));
      Draft draft;
      try {
        draft = analyst.analyze(prompt);
        validate(draft, selected);
      } catch (Exception e) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "AI analysis could not be completed or its citations were invalid. No analysis was"
                + " saved. Check model access, API billing and connectivity, then retry.");
      }
      Analysis result =
          new Analysis(UUID.randomUUID(), id, Instant.now(), analyst.model(), draft, selected);
      store.saveAnalysis(result);
      return result;
    } finally {
      admission.release();
    }
  }

  public static void validate(Draft draft, Context context) {
    if (draft == null
        || !text(draft.summary(), 2000)
        || draft.hypotheses() == null
        || draft.hypotheses().isEmpty()
        || draft.hypotheses().size() > 3) throw new IllegalArgumentException("Invalid AI result");
    Set<String> allowed = new HashSet<>();
    context.entries().forEach(e -> allowed.add(e.id()));
    for (Hypothesis h : draft.hypotheses())
      if (h == null
          || !text(h.title(), 180)
          || !text(h.reasoning(), 2000)
          || h.evidenceIds() == null
          || h.evidenceIds().isEmpty()
          || h.evidenceIds().size() > 10
          || !allowed.containsAll(h.evidenceIds())
          || new HashSet<>(h.evidenceIds()).size() != h.evidenceIds().size())
        throw new IllegalArgumentException("Invalid AI citations");
    checkList(draft.nextChecks(), 2, 5);
    checkList(draft.limitations(), 1, 5);
  }

  private static boolean text(String s, int limit) {
    return s != null && !s.isBlank() && s.length() <= limit;
  }

  private static void checkList(List<String> values, int min, int max) {
    if (values == null
        || values.size() < min
        || values.size() > max
        || values.stream().anyMatch(s -> !text(s, 600)))
      throw new IllegalArgumentException("Invalid AI list");
  }

  public Review review(UUID id, ReviewRequest request) {
    Analysis a = store.analysis(id, request.analysisId());
    if (request.hypothesisIndex() >= a.draft().hypotheses().size())
      throw new IllegalArgumentException("Hypothesis does not exist");
    Review review =
        new Review(
            UUID.randomUUID(),
            id,
            a.id(),
            request.hypothesisIndex(),
            request.status(),
            request.note().strip(),
            Instant.now());
    store.review(review);
    return review;
  }
}
