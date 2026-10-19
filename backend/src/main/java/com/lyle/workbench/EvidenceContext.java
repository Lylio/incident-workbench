package com.lyle.workbench;

import static com.lyle.workbench.Model.*;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class EvidenceContext {
  public static final int MAX_CONTEXT = 60;

  public Context select(Incident incident, List<String> ids) {
    Set<String> selected = new LinkedHashSet<>(ids);
    if (ids.size() > MAX_CONTEXT || selected.size() != ids.size())
      throw new IllegalArgumentException("Choose up to 60 distinct evidence IDs.");
    if (!selected.isEmpty()
        && !incident.entries().stream().map(Evidence::id).toList().containsAll(selected))
      throw new IllegalArgumentException("Unknown evidence ID in this investigation.");
    boolean automatic = selected.isEmpty();
    if (automatic) {
      // Prioritize errors with one chronological neighbor on each side, then WARN, then remaining
      // records.
      List<Evidence> all = incident.entries();
      for (int i = 0; i < all.size() && selected.size() < MAX_CONTEXT; i++)
        if (Set.of("ERROR", "FATAL").contains(all.get(i).level())) {
          selected.add(all.get(i).id());
          if (i > 0 && selected.size() < MAX_CONTEXT) selected.add(all.get(i - 1).id());
          if (i + 1 < all.size() && selected.size() < MAX_CONTEXT)
            selected.add(all.get(i + 1).id());
        }
      for (Evidence e : all)
        if (e.level().equals("WARN") && selected.size() < MAX_CONTEXT) selected.add(e.id());
      for (Evidence e : all) if (selected.size() < MAX_CONTEXT) selected.add(e.id());
    }
    List<Evidence> entries =
        incident.entries().stream()
            .filter(e -> selected.contains(e.id()))
            .map(
                e ->
                    new Evidence(
                        e.id(),
                        e.timestamp(),
                        e.service(),
                        e.level(),
                        e.traceId(),
                        clip(e.message(), 300),
                        e.fileName(),
                        e.lineNumber(),
                        ""))
            .toList();
    int chars = entries.stream().mapToInt(e -> e.message().length()).sum();
    return new Context(
        entries,
        incident.entries().size(),
        automatic
            ? "Error neighborhoods, warnings, then earliest remaining records"
            : "Explicit evidence selection",
        chars);
  }

  private String clip(String s, int n) {
    return s.length() <= n ? s : s.substring(0, n) + "… [excerpt clipped]";
  }
}
