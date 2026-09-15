package io.hookscope;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "hookscope.delivery.allowed-hosts=")
class DestinationAllowlistEmptyIntegrationTest {
  private static final String TOKEN = "isolated-integration-test-token-not-for-production";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10");

  @Autowired TestRestTemplate client;
  @Autowired JdbcTemplate jdbc;
  @LocalServerPort int port;

  @Test
  void emptyAllowlistDeniesDestinationCreation() {
    UUID endpoint = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO webhook_endpoints(id,name,public_key) VALUES (?,?,?)",
        endpoint,
        "empty",
        UUID.randomUUID().toString().replace("-", "").substring(0, 32));
    ResponseEntity<String> response =
        client.exchange(
            "http://localhost:" + port + "/api/v1/endpoints/" + endpoint + "/destinations",
            HttpMethod.POST,
            entity("{\"url\":\"https://receiver.example.com/hook\"}"),
            String.class);
    assertThat(response.getStatusCode().value()).isEqualTo(400);
  }

  private HttpEntity<String> entity(String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-HookScope-Admin-Token", TOKEN);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }
}
