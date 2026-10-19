package com.lyle.workbench;

import static com.lyle.workbench.Model.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AdmissionTest {
  @Test
  void oneAnalysisIsAdmittedAndThePermitIsReleased() throws Exception {
    UUID id = UUID.randomUUID();
    Incident incident =
        new Incident(
            id,
            Instant.EPOCH,
            "Incident",
            List.of(
                new Evidence(
                    "E0001", Instant.EPOCH, "order", "ERROR", "", "Timeout", "a.log", 1, "raw")),
            List.of(),
            List.of(),
            0);
    AtomicInteger saved = new AtomicInteger();
    WorkbenchStore store =
        new WorkbenchStore(null, new ObjectMapper().findAndRegisterModules()) {
          @Override
          public Incident incident(UUID key) {
            return incident;
          }

          @Override
          public void saveAnalysis(Analysis a) {
            saved.incrementAndGet();
          }
        };
    CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
    Analyst analyst =
        new Analyst() {
          public boolean available() {
            return true;
          }

          public String model() {
            return "TEST";
          }

          public Draft analyze(String prompt) {
            started.countDown();
            try {
              if (!release.await(5, TimeUnit.SECONDS))
                throw new IllegalStateException("Fixture timeout");
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(e);
            }
            return new Draft(
                "Observed timeout",
                List.of(
                    new Hypothesis("Possible dependency issue", "Unconfirmed", List.of("E0001"))),
                List.of("Check connectivity", "Check configuration"),
                List.of("Limited evidence"));
          }
        };
    InvestigationService service = new InvestigationService(store, new EvidenceContext(), analyst);
    AnalyzeRequest request = new AnalyzeRequest(List.of(), "");
    try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
      Future<Analysis> first = pool.submit(() -> service.analyze(id, request));
      try {
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> service.analyze(id, request))
            .isInstanceOfSatisfying(
                org.springframework.web.server.ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
      } finally {
        release.countDown();
      }
      first.get(5, TimeUnit.SECONDS);
      service.analyze(id, request);
      assertThat(saved.get()).isEqualTo(2);
    }
  }
}
