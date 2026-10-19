package com.lyle.workbench;

import static com.lyle.workbench.Model.*;

import java.util.*;

public final class Exports {
  private Exports() {}

  public static String csv(Incident incident) {
    StringBuilder out = new StringBuilder("id,timestamp,service,level,traceId,file,line,message\n");
    for (Evidence e : incident.entries())
      out.append(
              String.join(
                  ",",
                  List.of(
                      cell(e.id()),
                      cell(e.timestamp().toString()),
                      cell(e.service()),
                      cell(e.level()),
                      cell(e.traceId()),
                      cell(e.fileName()),
                      cell(String.valueOf(e.lineNumber())),
                      cell(e.message()))))
          .append("\n");
    return out.toString();
  }

  static String cell(String value) {
    String s = value;
    if (!s.stripLeading().isEmpty() && "=+@-".indexOf(s.stripLeading().charAt(0)) >= 0) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  private static String safe(String s) {
    return s.replace("\n", " ")
        .replace("\r", " ")
        .replace("`", "'")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }

  public static String markdown(Investigation v) {
    Incident i = v.incident();
    StringBuilder out =
        new StringBuilder(
            "# "
                + safe(i.title())
                + "\n\nStatus: "
                + v.status()
                + "\n\nImported: "
                + i.createdAt()
                + "\n\n");
    out.append("## Evidence coverage\n\n")
        .append(i.entries().size())
        .append(" parsed records; ")
        .append(i.rejectedLines())
        .append(
            " rejected nonblank lines. Timestamps normalized to UTC; clock skew is not"
                + " corrected.\n\n");
    for (Analysis a : v.analyses()) {
      out.append("## AI analysis ")
          .append(a.createdAt())
          .append("\n\nModel: ")
          .append(safe(a.model()))
          .append(". All hypotheses are unconfirmed suggestions.\n\n")
          .append(safe(a.draft().summary()))
          .append("\n\n");
      for (int h = 0; h < a.draft().hypotheses().size(); h++) {
        Hypothesis hyp = a.draft().hypotheses().get(h);
        final int idx = h;
        Optional<Review> review =
            v.reviews().stream()
                .filter(r -> r.analysisId().equals(a.id()) && r.hypothesisIndex() == idx)
                .findFirst();
        out.append("### ")
            .append(safe(hyp.title()))
            .append("\n\n")
            .append(safe(hyp.reasoning()))
            .append("\n\nEvidence: ")
            .append(String.join(", ", hyp.evidenceIds()))
            .append("\n\nHuman review: ")
            .append(review.map(r -> r.status() + " — " + safe(r.note())).orElse("UNREVIEWED"))
            .append("\n\n");
      }
      out.append("### Suggested next checks\n\n");
      a.draft().nextChecks().forEach(c -> out.append("- ").append(safe(c)).append("\n"));
      out.append("\n### Limitations\n\n");
      a.draft().limitations().forEach(c -> out.append("- ").append(safe(c)).append("\n"));
      out.append("\n");
    }
    out.append("## Evidence register\n\n");
    for (Evidence e : i.entries())
      out.append("- **")
          .append(e.id())
          .append("** ")
          .append(e.timestamp())
          .append(" · ")
          .append(safe(e.service()))
          .append(" · ")
          .append(e.level())
          .append(" · ")
          .append(safe(e.fileName()))
          .append(":")
          .append(e.lineNumber())
          .append(" — ")
          .append(safe(e.message()))
          .append("\n");
    return out.toString();
  }
}
