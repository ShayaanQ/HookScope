package io.hookscope;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "hookscope.additional-sensitive-headers=X-Custom-Secret")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdditionalRedactionIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private TestRestTemplate rest;
  @LocalServerPort private int port;
  private String publicKey;

  @BeforeEach
  void setup() {
    jdbc.update("DELETE FROM webhook_events");
    jdbc.update("DELETE FROM webhook_endpoints");
    publicKey = "R".repeat(32);
    jdbc.update(
        "INSERT INTO webhook_endpoints (id,name,public_key,created_at) VALUES (?,?,?,?)",
        UUID.randomUUID(),
        "Redaction",
        publicKey,
        java.sql.Timestamp.from(Instant.now()));
  }

  @Test
  void redactsConfiguredExactNameCaseInsensitivelyButNotSubstrings() {
    String sensitiveValue = "runtime-sensitive-" + UUID.randomUUID();
    HttpHeaders headers = new HttpHeaders();
    headers.add("x-CUSTOM-secret", sensitiveValue);
    headers.add("X-Custom-Secret-Extra", "must-remain");
    ResponseEntity<String> response =
        rest.exchange(
            "http://localhost:" + port + "/hooks/" + publicKey,
            HttpMethod.POST,
            new HttpEntity<>(new byte[0], headers),
            String.class);
    assertThat(response.getStatusCode().value()).isEqualTo(204);
    String headersJson =
        jdbc.queryForObject("SELECT headers::text FROM webhook_events", String.class);
    assertThat(headersJson)
        .contains("[REDACTED]")
        .contains("must-remain")
        .doesNotContain(sensitiveValue);
  }
}
