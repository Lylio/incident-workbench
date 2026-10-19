package com.lyle.workbench;

import static com.lyle.workbench.Model.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired ScriptedAnalyst analyst;

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    ScriptedAnalyst scripted() {
      return new ScriptedAnalyst();
    }
  }

  static class ScriptedAnalyst implements Analyst {
    boolean enabled = true, bad = false, fail = false;

    public boolean available() {
      return enabled;
    }

    public String model() {
      return "TEST-STUB-NOT-A-LIVE-MODEL";
    }

    public Draft analyze(String prompt) {
      if (fail) throw new IllegalStateException("Test outage");
      return new Draft(
          "Payment connection failures are observed; the cause is unconfirmed.",
          List.of(
              new Hypothesis(
                  "Dependency may be unreachable",
                  "The selected evidence warrants a connectivity check.",
                  List.of(bad ? "E9999" : "E0001"))),
          List.of("Check processor connectivity", "Compare configuration history"),
          List.of("No network trace or previous configuration provided."));
    }
  }

  JsonNode sample() throws Exception {
    return json.readTree(
        mvc.perform(post("/api/incidents/sample"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void sampleIsParsedStoredReopenedAndExported() throws Exception {
    JsonNode v = sample();
    String id = v.path("incident").path("id").asText();
    assertThat(v.path("incident").path("entries").size()).isEqualTo(34);
    assertThat(v.path("incident").path("rejectedLines").asInt()).isZero();
    mvc.perform(get("/api/incidents/" + id))
        .andExpect(status().isOk())
        .andExpect(content().json(v.toString()));
    mvc.perform(get("/api/incidents/" + id + "/export.json"))
        .andExpect(status().isOk())
        .andExpect(header().exists("Content-Disposition"));
    mvc.perform(get("/api/incidents/" + id + "/evidence.csv"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("E0001")));
    mvc.perform(get("/api/incidents/" + id + "/report.md"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Evidence register")));
    mvc.perform(
            put("/api/incidents/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));
  }

  @Test
  void multipartAndPartialImportsWork() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "files",
            "a.log",
            "text/plain",
            "bad\n2026-10-18T14:00:00Z ERROR [order] Test".getBytes());
    mvc.perform(multipart("/api/incidents").file(file).param("title", "My incident"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.incident.rejectedLines").value(1));
  }

  @Test
  void invalidImportsDoNotCreateRecords() throws Exception {
    int before =
        json.readTree(
                mvc.perform(get("/api/incidents")).andReturn().getResponse().getContentAsString())
            .size();
    mvc.perform(
            multipart("/api/incidents")
                .file(
                    new MockMultipartFile("files", "bad.log", "text/plain", "nonsense".getBytes()))
                .param("title", "Bad"))
        .andExpect(status().isBadRequest());
    assertThat(
            json.readTree(
                    mvc.perform(get("/api/incidents"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .size())
        .isEqualTo(before);
  }

  @Test
  void analysisHasValidCitationsAndReviewAuditIsAppendOnly() throws Exception {
    String id = sample().path("incident").path("id").asText();
    JsonNode a =
        json.readTree(
            mvc.perform(
                    post("/api/incidents/" + id + "/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evidenceIds\":[\"E0001\"],\"question\":\"Investigate\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(a.path("context").path("entries").size()).isEqualTo(1);
    String aid = a.path("id").asText();
    for (String status : List.of("ACCEPTED", "REJECTED"))
      mvc.perform(
              post("/api/incidents/" + id + "/reviews")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"analysisId\":\""
                          + aid
                          + "\",\"hypothesisIndex\":0,\"status\":\""
                          + status
                          + "\",\"note\":\"Review note\"}"))
          .andExpect(status().isCreated());
    mvc.perform(get("/api/incidents/" + id))
        .andExpect(jsonPath("$.reviews.length()").value(2))
        .andExpect(jsonPath("$.reviews[0].status").value("REJECTED"));
    String other = sample().path("incident").path("id").asText();
    mvc.perform(
            post("/api/incidents/" + other + "/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"analysisId\":\""
                        + aid
                        + "\",\"hypothesisIndex\":0,\"status\":\"ACCEPTED\",\"note\":\"\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void invalidCitationOrProviderFailureCannotSaveAnAnalysis() throws Exception {
    String id = sample().path("incident").path("id").asText();
    try {
      analyst.bad = true;
      analyze(id, 502);
      analyst.bad = false;
      analyst.fail = true;
      analyze(id, 502);
    } finally {
      analyst.bad = false;
      analyst.fail = false;
    }
    mvc.perform(get("/api/incidents/" + id)).andExpect(jsonPath("$.analyses.length()").value(0));
  }

  @Test
  void missingKeyIsExplicitAndEvidenceRemainsUsable() throws Exception {
    String id = sample().path("incident").path("id").asText();
    try {
      analyst.enabled = false;
      analyze(id, 503);
      mvc.perform(
              post("/api/incidents/" + id + "/context")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"evidenceIds\":[]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.entries.length()").value(34));
    } finally {
      analyst.enabled = true;
    }
  }

  @Test
  void malformedIdsAndOutOfScopeEvidenceFail() throws Exception {
    mvc.perform(get("/api/incidents/not-a-uuid")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/incidents/00000000-0000-0000-0000-000000000000"))
        .andExpect(status().isNotFound());
    String id = sample().path("incident").path("id").asText();
    mvc.perform(
            post("/api/incidents/" + id + "/context")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"evidenceIds\":[\"E9999\"]}"))
        .andExpect(status().isBadRequest());
  }

  void analyze(String id, int expected) throws Exception {
    mvc.perform(
            post("/api/incidents/" + id + "/analyses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"evidenceIds\":[],\"question\":\"\"}"))
        .andExpect(status().is(expected));
  }
}
