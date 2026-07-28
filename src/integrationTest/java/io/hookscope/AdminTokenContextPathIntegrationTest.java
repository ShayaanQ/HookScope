package io.hookscope;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.servlet.context-path=/management-context")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminTokenContextPathIntegrationTest {

  private static final String ADMIN_TOKEN = "isolated-integration-test-token-not-for-production";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @LocalServerPort private int port;

  @Test
  void protectsManagementRoutesWithinANonRootContextPathWhileHealthRemainsPublic() {
    jdbcTemplate.update("DELETE FROM webhook_endpoints");
    ResponseEntity<String> missingToken =
        restTemplate.getForEntity(url("/management-context/api/v1/endpoints"), String.class);
    assertThat(missingToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(missingToken.getBody()).doesNotContain(ADMIN_TOKEN);

    HttpHeaders headers = new HttpHeaders();
    headers.set("X-HookScope-Admin-Token", ADMIN_TOKEN);
    ResponseEntity<String> validToken =
        restTemplate.exchange(
            url("/management-context/api/v1/endpoints"),
            org.springframework.http.HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);
    assertThat(validToken.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            restTemplate
                .getForEntity(url("/management-context/actuator/health"), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }
}
