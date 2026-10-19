package com.lyle.workbench;

import static com.lyle.workbench.Model.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAiAdapterTest {
  @Test
  void realSpringAiClientSendsEvidenceAndConvertsStructuredOutputAgainstLocalFixture()
      throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    Draft expected =
        new Draft(
            "Observed timeout",
            List.of(
                new Hypothesis("Dependency unavailable", "Check the dependency", List.of("E0001"))),
            List.of("Check connectivity", "Compare configuration"),
            List.of("Only one excerpt supplied"));
    String draft = mapper.writeValueAsString(expected);
    AtomicReference<String> request = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          String response =
              mapper.writeValueAsString(
                  Map.of(
                      "id",
                      "fixture",
                      "object",
                      "chat.completion",
                      "created",
                      1,
                      "model",
                      "gpt-4.1-mini",
                      "choices",
                      List.of(
                          Map.of(
                              "index",
                              0,
                              "message",
                              Map.of("role", "assistant", "content", draft),
                              "finish_reason",
                              "stop"))));
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      var client =
          OpenAiAnalyst.connect(
              "test-key-not-real",
              "gpt-4.1-mini",
              "http://127.0.0.1:" + server.getAddress().getPort());
      Draft actual =
          client
              .prompt()
              .system(OpenAiAnalyst.SYSTEM)
              .user("Evidence E0001: processor connection timed out")
              .call()
              .entity(Draft.class);
      assertThat(actual).isEqualTo(expected);
      JsonNodeLike.check(mapper, request.get());
    } finally {
      server.stop(0);
    }
  }

  static class JsonNodeLike {
    static void check(ObjectMapper mapper, String text) throws Exception {
      var n = mapper.readTree(text);
      assertThat(n.path("messages").toString()).contains("E0001", "untrusted DATA");
      assertThat(n.path("model").asText()).isEqualTo("gpt-4.1-mini");
      assertThat(n.path("max_tokens").asInt()).isEqualTo(1800);
    }
  }
}
