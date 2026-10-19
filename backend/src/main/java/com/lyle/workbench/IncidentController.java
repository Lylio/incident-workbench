package com.lyle.workbench;

import static com.lyle.workbench.Model.*;

import jakarta.validation.Valid;
import java.io.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class IncidentController {
  private final WorkbenchStore store;
  private final LogImporter importer;
  private final EvidenceContext context;
  private final InvestigationService service;
  private final Analyst analyst;

  public IncidentController(
      WorkbenchStore s, LogImporter p, EvidenceContext c, InvestigationService x, Analyst a) {
    store = s;
    importer = p;
    context = c;
    service = x;
    analyst = a;
  }

  @GetMapping("/config")
  public Map<String, Object> config() {
    return Map.of(
        "aiConfigured",
        analyst.available(),
        "model",
        analyst.model(),
        "maxContextEntries",
        60,
        "maxEntries",
        4000,
        "version",
        "1.0.0");
  }

  @GetMapping("/incidents")
  public List<Summary> recent() {
    return store.recent();
  }

  @GetMapping("/incidents/{id}")
  public Investigation view(@PathVariable UUID id) {
    return store.view(id);
  }

  @PostMapping(value = "/incidents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Investigation create(
      @RequestParam String title, @RequestParam("files") List<MultipartFile> files)
      throws IOException {
    if (files.size() > 6) throw new IllegalArgumentException("Choose up to 6 files.");
    List<Upload> uploads = new ArrayList<>();
    for (MultipartFile f : files) uploads.add(new Upload(f.getOriginalFilename(), f.getBytes()));
    Incident i = importer.parse(title, uploads);
    store.save(i);
    return store.view(i.id());
  }

  @PostMapping("/incidents/sample")
  @ResponseStatus(HttpStatus.CREATED)
  public Investigation sample() throws IOException {
    List<Upload> files = new ArrayList<>();
    for (String name : List.of("order.log", "inventory.log", "payment.jsonl"))
      try (InputStream in = getClass().getResourceAsStream("/samples/" + name)) {
        if (in == null) throw new IllegalStateException("Missing sample");
        files.add(new Upload(name, in.readAllBytes()));
      }
    Incident i = importer.parse("Checkout failures after deployment", files);
    store.save(i);
    return store.view(i.id());
  }

  @PostMapping("/incidents/{id}/context")
  public Context preview(@PathVariable UUID id, @Valid @RequestBody ContextRequest r) {
    return context.select(store.incident(id), r.evidenceIds());
  }

  @PostMapping("/incidents/{id}/analyses")
  @ResponseStatus(HttpStatus.CREATED)
  public Analysis analyze(@PathVariable UUID id, @Valid @RequestBody AnalyzeRequest r) {
    return service.analyze(id, r);
  }

  @PostMapping("/incidents/{id}/reviews")
  @ResponseStatus(HttpStatus.CREATED)
  public Review review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest r) {
    return service.review(id, r);
  }

  @PutMapping("/incidents/{id}/status")
  public Investigation status(@PathVariable UUID id, @Valid @RequestBody StatusRequest r) {
    store.status(id, r.status());
    return store.view(id);
  }

  @GetMapping("/incidents/{id}/export.json")
  public ResponseEntity<Investigation> json(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=incident-" + id + ".json")
        .body(store.view(id));
  }

  @GetMapping("/incidents/{id}/evidence.csv")
  public ResponseEntity<String> csv(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=evidence-" + id + ".csv")
        .body(Exports.csv(store.incident(id)));
  }

  @GetMapping("/incidents/{id}/report.md")
  public ResponseEntity<String> markdown(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=incident-" + id + ".md")
        .body(Exports.markdown(store.view(id)));
  }
}
