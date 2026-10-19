package com.lyle.workbench;

import static com.lyle.workbench.Model.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class ImporterTest {
  final LogImporter parser = new LogImporter(new ObjectMapper());

  Upload file(String name, String text) {
    return new Upload(name, text.getBytes(StandardCharsets.UTF_8));
  }

  String text(String time, String message) {
    return time + " ERROR [payment] [trace=req-1] " + message;
  }

  @Test
  void textAndJsonAreNormalizedAndSortedWithExactSourceLocations() {
    String a = text("2026-10-18T15:00:01+01:00", "Timed out");
    String b =
        "{\"timestamp\":\"2026-10-18T14:00:00Z\",\"service\":\"order\",\"level\":\"INFO\",\"message\":\"Started\"}";
    Incident i =
        parser.parse("Test", List.of(file("payment.log", "\n" + a + "\n"), file("order.jsonl", b)));
    assertThat(i.entries()).hasSize(2);
    assertThat(i.entries().getFirst().service()).isEqualTo("order");
    assertThat(i.entries().get(1).lineNumber()).isEqualTo(2);
    assertThat(i.entries().get(1).timestamp().toString()).isEqualTo("2026-10-18T14:00:01Z");
    assertThat(i.entries().get(1).raw()).isEqualTo(a);
    assertThat(i.entries().getFirst().id()).isEqualTo("E0001");
    assertThat(i.entries().get(1).traceId()).isEqualTo("req-1");
  }

  @Test
  void rejectedLinesRemainInSourceAndCoverageIsExplicit() {
    Incident i =
        parser.parse(
            "Test", List.of(file("a.log", "bad line\n" + text("2026-10-18T14:00:00Z", "Timeout"))));
    assertThat(i.rejectedLines()).isEqualTo(1);
    assertThat(i.diagnostics().getFirst().lineNumber()).isEqualTo(1);
    assertThat(i.sources().getFirst().content()).startsWith("bad line");
    assertThat(i.entries().getFirst().lineNumber()).isEqualTo(2);
  }

  @Test
  void equalTimestampsPreserveFileAndLineOrder() {
    String t = "2026-10-18T14:00:00Z";
    Incident i =
        parser.parse(
            "Test", List.of(file("b.log", text(t, "First")), file("a.log", text(t, "Second"))));
    assertThat(i.entries().stream().map(Evidence::message)).containsExactly("First", "Second");
  }

  @Test
  void utf8BomAndCrLfWork() {
    Incident i =
        parser.parse(
            "Test",
            List.of(
                file(
                    "a.log",
                    "\ufeff"
                        + text("2026-10-18T14:00:00Z", "First")
                        + "\r\n"
                        + text("2026-10-18T14:00:01Z", "Second"))));
    assertThat(i.entries()).hasSize(2);
    assertThat(i.entries().get(1).lineNumber()).isEqualTo(2);
  }

  @Test
  void invalidEncodingAndUnsafeOrDuplicateNamesAreRejected() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> parser.parse("T", List.of(new Upload("a.log", new byte[] {(byte) 0xff}))));
    for (String n : List.of("../a.log", "a/b.log", "a.exe"))
      assertThatIllegalArgumentException()
          .isThrownBy(() -> parser.parse("T", List.of(file(n, "x"))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> parser.parse("T", List.of(file("a.log", "x"), file("A.log", "x"))));
  }

  @Test
  void missingOffsetsAndMalformedJsonDoNotBecomeFabricatedTimestamps() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> parser.parse("T", List.of(file("a.log", text("2026-10-18T14:00:00", "Bad")))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> parser.parse("T", List.of(file("a.jsonl", "{bad"))));
  }

  @Test
  void inputBoundsAreEnforcedBeforeSaving() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> parser.parse("T", List.of(new Upload("a.log", new byte[1024 * 1024 + 1]))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> parser.parse("T", List.of(file("a.log", ("x\n").repeat(4001)))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> parser.parse(" ", List.of(file("a.log", "x"))));
  }

  @Test
  void sourceLineLimitProducesADiagnostic() {
    Incident i =
        parser.parse(
            "T",
            List.of(file("a.log", "x".repeat(8193) + "\n" + text("2026-10-18T14:00:00Z", "Good"))));
    assertThat(i.rejectedLines()).isEqualTo(1);
    assertThat(i.entries()).hasSize(1);
  }

  @Test
  void contextIsBoundedAndNeverIncludesRawSource() {
    List<Evidence> entries = new ArrayList<>();
    for (int j = 0; j < 100; j++)
      entries.add(
          new Evidence(
              "E%04d".formatted(j + 1),
              java.time.Instant.EPOCH.plusSeconds(j),
              "order",
              j == 90 ? "ERROR" : "INFO",
              "",
              "x".repeat(800),
              "a.log",
              j + 1,
              "SECRET RAW"));
    Incident i =
        new Incident(
            UUID.randomUUID(), java.time.Instant.now(), "Test", entries, List.of(), List.of(), 0);
    Context c = new EvidenceContext().select(i, List.of());
    assertThat(c.entries()).hasSize(60);
    assertThat(c.entries().stream().map(Evidence::id)).contains("E0091");
    assertThat(c.entries()).allMatch(e -> e.raw().isEmpty() && e.message().length() < 330);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EvidenceContext().select(i, List.of("E9999")));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EvidenceContext().select(i, List.of("E0001", "E0001")));
  }

  @Test
  void unknownOrOutOfSelectionCitationsAreRejected() {
    Context c =
        new Context(
            List.of(
                new Evidence(
                    "E0001",
                    java.time.Instant.EPOCH,
                    "order",
                    "INFO",
                    "",
                    "Started",
                    "a.log",
                    1,
                    "")),
            2,
            "explicit",
            7);
    Draft bad =
        new Draft(
            "Possible cause",
            List.of(new Hypothesis("Hypothesis", "Reason", List.of("E0002"))),
            List.of("Check one", "Check two"),
            List.of("Missing logs"));
    assertThatIllegalArgumentException().isThrownBy(() -> InvestigationService.validate(bad, c));
  }

  @Test
  void csvEscapesQuotesNewlinesAndFormulaPrefixes() {
    assertThat(Exports.cell("=SUM(1,2)")).isEqualTo("\"'=SUM(1,2)\"");
    assertThat(Exports.cell("a\"b\nc")).isEqualTo("\"a\"\"b\nc\"");
  }
}
