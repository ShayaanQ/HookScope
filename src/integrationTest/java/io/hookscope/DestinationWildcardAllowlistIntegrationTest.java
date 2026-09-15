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
@TestPropertySource(properties = "hookscope.delivery.allowed-hosts=*.example.com")
class DestinationWildcardAllowlistIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10");

  @Autowired TestRestTemplate client;
  @Autowired JdbcTemplate jdbc;
  @LocalServerPort int port;

  @Test
  void wildcardEntryDoesNotAuthorizeSubdomains() {
    UUID endpoint = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO webhook_endpoints(id,name,public_key) VALUES (?,?,?)",
        endpoint,
        "wildcard",
        UUID.randomUUID().toString().replace("-", "").substring(0, 32));
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-HookScope-Admin-Token", "isolated-integration-test-token-not-for-production");
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> response =
        client.exchange(
            "http://localhost:" + port + "/api/v1/endpoints/" + endpoint + "/destinations",
            HttpMethod.POST,
            new HttpEntity<>("{\"url\":\"https://sub.example.com/hook\"}", headers),
            String.class);
    assertThat(response.getStatusCode().value()).isEqualTo(400);
  }
}
