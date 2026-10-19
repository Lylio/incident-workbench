package com.lyle.workbench;

import static com.lyle.workbench.Model.*;

import com.fasterxml.jackson.databind.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;

@Component
public class LogImporter {
  public static final int MAX_ENTRIES = 4000,
      MAX_FILES = 6,
      MAX_BYTES = 3 * 1024 * 1024,
      MAX_LINE = 8192;
  private final ObjectMapper json;
  private static final Pattern TEXT =
      Pattern.compile(
          "^(\\S+)\\s+(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+\\[([A-Za-z0-9_.-]{1,60})\\](?:\\s+\\[trace=([^\\]]{1,100})\\])?\\s+(.*)$");

  public LogImporter(ObjectMapper json) {
    this.json = json;
  }

  public Incident parse(String title, List<Upload> files) {
    if (title == null || title.isBlank() || title.strip().length() > 120)
      throw new IllegalArgumentException("Use an investigation title of 1–120 characters.");
    if (files == null || files.isEmpty() || files.size() > MAX_FILES)
      throw new IllegalArgumentException("Import 1–6 UTF-8 log, txt or jsonl files.");
    int bytes = 0, lines = 0, rejected = 0;
    Set<String> names = new HashSet<>();
    List<Evidence> entries = new ArrayList<>();
    List<Source> sources = new ArrayList<>();
    List<Diagnostic> diagnostics = new ArrayList<>();
    for (Upload file : files) {
      String name = file.name();
      if (name == null
          || !name.matches("[A-Za-z0-9][A-Za-z0-9_. -]{0,99}\\.(log|txt|jsonl)")
          || !names.add(name.toLowerCase(Locale.ROOT)))
        throw new IllegalArgumentException(
            "Use unique plain filenames ending in .log, .txt or .jsonl (no paths).");
      bytes += file.bytes().length;
      if (file.bytes().length > 1024 * 1024 || bytes > MAX_BYTES)
        throw new IllegalArgumentException("Each file must be ≤1 MiB; total input ≤3 MiB.");
      String content;
      try {
        content =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(file.bytes()))
                .toString();
      } catch (CharacterCodingException e) {
        throw new IllegalArgumentException(name + " is not valid UTF-8.");
      }
      if (content.startsWith("\ufeff")) content = content.substring(1);
      String[] rows = content.split("\\R", -1);
      int parsed = 0, bad = 0, total = 0;
      for (int i = 0; i < rows.length; i++) {
        String row = rows[i];
        if (row.isBlank()) continue;
        total++;
        if (++lines > MAX_ENTRIES)
          throw new IllegalArgumentException(
              "Import contains more than 4000 nonblank lines; split the incident into smaller"
                  + " files.");
        try {
          if (row.length() > MAX_LINE)
            throw new IllegalArgumentException("Line exceeds 8192 characters");
          entries.add(parseLine(name, i + 1, row));
          parsed++;
        } catch (Exception e) {
          bad++;
          rejected++;
          if (diagnostics.size() < 100)
            diagnostics.add(
                new Diagnostic(
                    name,
                    i + 1,
                    "Unsupported or invalid log line; expected an offset timestamp, service, level"
                        + " and message."));
        }
      }
      sources.add(new Source(name, total, parsed, bad, content));
    }
    if (entries.stream().map(Evidence::service).distinct().count() > 12)
      throw new IllegalArgumentException("Use at most 12 services in one investigation.");
    if (entries.isEmpty())
      throw new IllegalArgumentException(
          "No supported log records found. See the sample files and log format documentation.");
    // List.sort is stable; identical timestamps retain upload-file/line order.
    entries.sort(Comparator.comparing(Evidence::timestamp));
    List<Evidence> identified = new ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      Evidence e = entries.get(i);
      identified.add(
          new Evidence(
              "E%04d".formatted(i + 1),
              e.timestamp(),
              e.service(),
              e.level(),
              e.traceId(),
              e.message(),
              e.fileName(),
              e.lineNumber(),
              e.raw()));
    }
    return new Incident(
        UUID.randomUUID(),
        Instant.now(),
        title.strip(),
        List.copyOf(identified),
        List.copyOf(sources),
        List.copyOf(diagnostics),
        rejected);
  }

  private Evidence parseLine(String name, int line, String raw) throws Exception {
    String timestamp, service, level, message, trace;
    if (raw.stripLeading().startsWith("{")) {
      JsonNode n = json.readTree(raw);
      timestamp = required(n, "timestamp");
      service = required(n, "service");
      level = required(n, "level");
      message = required(n, "message");
      trace = n.path("traceId").asText("");
    } else {
      Matcher m = TEXT.matcher(raw);
      if (!m.matches()) throw new IllegalArgumentException();
      timestamp = m.group(1);
      level = m.group(2);
      service = m.group(3);
      trace = Objects.toString(m.group(4), "");
      message = m.group(5);
    }
    Instant time = OffsetDateTime.parse(timestamp).toInstant();
    if (!service.matches("[A-Za-z0-9_.-]{1,60}")
        || !Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL").contains(level)
        || trace.length() > 100
        || message.isBlank()
        || message.length() > 6000) throw new IllegalArgumentException();
    return new Evidence("", time, service, level, trace, message, name, line, raw);
  }

  private String required(JsonNode n, String key) {
    JsonNode v = n.get(key);
    if (v == null || !v.isTextual() || v.asText().isBlank()) throw new IllegalArgumentException();
    return v.asText();
  }
}
