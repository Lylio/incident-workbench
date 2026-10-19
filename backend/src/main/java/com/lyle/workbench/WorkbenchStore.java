package com.lyle.workbench;

import static com.lyle.workbench.Model.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class WorkbenchStore {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public WorkbenchStore(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public String encode(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException("Serialization failed", e);
    }
  }

  private <T> T decode(String text, Class<T> t) {
    try {
      return json.readValue(text, t);
    } catch (Exception e) {
      throw new IllegalStateException("Stored report could not be read", e);
    }
  }

  public void save(Incident i) {
    db.update(
        "INSERT INTO incidents(id,created_at,title,status,report) VALUES (?,?,?,'OPEN',?)",
        i.id(),
        java.sql.Timestamp.from(i.createdAt()),
        i.title(),
        encode(i));
  }

  public Incident incident(UUID id) {
    return db
        .query(
            "SELECT report FROM incidents WHERE id=?",
            (rs, n) -> decode(rs.getString(1), Incident.class),
            id)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation not found"));
  }

  public List<Summary> recent() {
    return db.query(
        "SELECT report,status FROM incidents ORDER BY created_at DESC,id LIMIT 50",
        (rs, n) -> {
          Incident i = decode(rs.getString(1), Incident.class);
          return new Summary(
              i.id(),
              i.createdAt(),
              i.title(),
              Status.valueOf(rs.getString(2)),
              i.entries().size(),
              (int)
                  i.entries().stream()
                      .filter(e -> e.level().equals("ERROR") || e.level().equals("FATAL"))
                      .count(),
              (int) i.entries().stream().map(Evidence::service).distinct().count(),
              i.rejectedLines());
        });
  }

  public Investigation view(UUID id) {
    Incident i = incident(id);
    Status s =
        Status.valueOf(
            db.queryForObject("SELECT status FROM incidents WHERE id=?", String.class, id));
    return new Investigation(i, s, analyses(id), reviews(id));
  }

  public List<Analysis> analyses(UUID id) {
    return db.query(
        "SELECT report FROM analyses WHERE incident_id=? ORDER BY created_at DESC,id LIMIT 10",
        (rs, n) -> decode(rs.getString(1), Analysis.class),
        id);
  }

  public Analysis analysis(UUID incidentId, UUID analysisId) {
    return db
        .query(
            "SELECT report FROM analyses WHERE id=? AND incident_id=?",
            (rs, n) -> decode(rs.getString(1), Analysis.class),
            analysisId,
            incidentId)
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Analysis not found in this investigation"));
  }

  public void saveAnalysis(Analysis a) {
    db.update(
        "INSERT INTO analyses(id,incident_id,created_at,report) VALUES (?,?,?,?)",
        a.id(),
        a.incidentId(),
        java.sql.Timestamp.from(a.createdAt()),
        encode(a));
  }

  public void status(UUID id, Status status) {
    if (db.update("UPDATE incidents SET status=? WHERE id=?", status.name(), id) == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation not found");
  }

  public void review(Review r) {
    db.update(
        "INSERT INTO reviews(id,incident_id,analysis_id,hypothesis_index,status,note,created_at)"
            + " VALUES (?,?,?,?,?,?,?)",
        r.id(),
        r.incidentId(),
        r.analysisId(),
        r.hypothesisIndex(),
        r.status().name(),
        r.note(),
        java.sql.Timestamp.from(r.createdAt()));
  }

  public List<Review> reviews(UUID incidentId) {
    return db.query(
        "SELECT id,analysis_id,hypothesis_index,status,note,created_at FROM reviews WHERE"
            + " incident_id=? ORDER BY created_at DESC,id",
        (rs, n) ->
            new Review(
                rs.getObject(1, UUID.class),
                incidentId,
                rs.getObject(2, UUID.class),
                rs.getInt(3),
                Decision.valueOf(rs.getString(4)),
                rs.getString(5),
                rs.getTimestamp(6).toInstant()),
        incidentId);
  }
}
