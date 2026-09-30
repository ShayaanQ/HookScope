package io.hookscope;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
@TestPropertySource(
    properties = "hookscope.delivery.allowed-hosts=receiver.example.com,127.0.0.1,::1")
@ExtendWith(OutputCaptureExtension.class)
class DestinationManagementIntegrationTest {
  private static final String TOKEN = "isolated-integration-test-token-not-for-production";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10");

  @Autowired JdbcTemplate jdbc;
  @Autowired TestRestTemplate client;
  @Autowired ObjectMapper mapper;
  @LocalServerPort int port;

  @BeforeEach
  void clear() {
    jdbc.update("DELETE FROM webhook_destinations");
    jdbc.update("DELETE FROM webhook_endpoints");
  }

  @Test
  void schemaAndMigrationAreLocked() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
        .isEqualTo(4);
    assertColumn("id", "uuid", "NO");
    assertColumn("endpoint_id", "uuid", "NO");
    assertColumn("url", "text", "NO");
    assertColumn("created_at", "timestamp with time zone", "NO");
    assertThat(
            jdbc.queryForObject(
                "SELECT column_default FROM information_schema.columns WHERE table_name='webhook_destinations' AND column_name='created_at'",
                String.class))
        .contains("CURRENT_TIMESTAMP");
    assertThat(
            jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='webhook_destinations_pkey'",
                String.class))
        .isEqualTo("PRIMARY KEY (id)");
    assertThat(
            jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='webhook_destinations_endpoint_url_key'",
                String.class))
        .isEqualTo("UNIQUE (endpoint_id, url)");
    assertThat(
            jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname='webhook_destinations_endpoint_created_at_id_desc_idx'",
                String.class))
        .contains("endpoint_id, created_at DESC, id DESC");
    assertThat(
            jdbc.queryForObject(
                "SELECT delete_rule FROM information_schema.referential_constraints WHERE constraint_name='webhook_destinations_endpoint_fk'",
                String.class))
        .isEqualTo("NO ACTION");
    assertThat(
            jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='webhook_destinations_endpoint_fk'",
                String.class))
        .contains("FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoints(id)");
  }

  @Test
  void createsGetsListsAndRejectsCrossEndpointAccess() throws Exception {
    UUID endpoint = endpoint("receiver.example.com");
    JsonNode created = json(post(endpoint, "https://receiver.example.com/webhooks?marker=secret"));
    assertThat(created.get("url").asText())
        .isEqualTo("https://receiver.example.com/webhooks?marker=secret");
    assertThat(get(endpoint, UUID.fromString(created.get("id").asText())).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(json(list(endpoint)).get("content")).hasSize(1);
    UUID other = endpoint("other");
    assertThat(get(other, UUID.fromString(created.get("id").asText())).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    for (HttpMethod method : List.of(HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
      assertThat(
              client
                  .exchange(
                      url("/api/v1/endpoints/" + endpoint + "/destinations/" + created.get("id")),
                      method,
                      entity("{}"),
                      String.class)
                  .getStatusCode())
          .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }
    assertThat(get(endpoint, UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(list(UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void validatesAllowlistAndDuplicates(CapturedOutput output) throws Exception {
    UUID endpoint = endpoint("one");
    String duplicateUrl =
        "https://RECEIVER.EXAMPLE.COM:8443/x?duplicate-marker=" + UUID.randomUUID();
    assertThat(post(endpoint, duplicateUrl).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    ResponseEntity<String> duplicate = post(endpoint, duplicateUrl);
    assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(duplicate.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(duplicate.getBody())
        .doesNotContain("duplicate-marker", "webhook_destinations_endpoint_url_key", "constraint");
    assertThat(output.getAll())
        .doesNotContain(
            duplicateUrl, "webhook_destinations_endpoint_url_key", TOKEN, "duplicate-marker");
    assertThat(post(endpoint, "https://sub.receiver.example.com/x").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(post(endpoint, "https://receiver.example.com:bad/x").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(post(endpoint, "relative/path").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(post(endpoint, "ftp://receiver.example.com/x").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(post(endpoint, "https://user:pass@receiver.example.com/x").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(post(endpoint, "https://receiver.example.com/x#fragment").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(post(endpoint, "https://receiver.example.com:65536/x").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(post(endpoint, "https://receiver.example.com:-1/x").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(post(endpoint, "https://evilreceiver.example.com/x").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(post(endpoint, "https://*.example.com/x").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(post(endpoint, "http://127.0.0.1:65535/x").getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(post(endpoint, "http://[::1]/ipv6").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(post(endpoint, "http://192.0.2.1/x").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    ResponseEntity<String> invalid =
        post(
            endpoint,
            "https://user:pass@receiver.example.com/x?privacy-marker-" + UUID.randomUUID());
    assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(invalid.getBody()).doesNotContain("privacy-marker");
  }

  @Test
  void longUrlsMissingHostsAndSameUrlAcrossEndpoints() throws Exception {
    UUID first = endpoint("first");
    UUID second = endpoint("second");
    String longUrl = "https://receiver.example.com/" + "a".repeat(2100);
    assertThat(post(first, longUrl).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(post(first, "https://receiver.example.com/same").getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(post(second, "https://receiver.example.com/same").getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(post(first, "https://receiver.example.com/same").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    ResponseEntity<String> missingHost = post(first, "https:///missing-host");
    assertProblem(missingHost, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
  }

  @Test
  void destinationPrivacyMarkersAreAbsentFromCapturedOutput(CapturedOutput output) {
    UUID endpoint = endpoint("privacy");
    String marker = "query-marker-" + UUID.randomUUID();
    ResponseEntity<String> invalid =
        post(endpoint, "https://user:pass@receiver.example.com/?" + marker);
    assertThat(invalid.getBody()).doesNotContain(marker);
    assertThat(output.getAll()).doesNotContain(marker, TOKEN);
  }

  @Test
  void destinationListUsesM1PaginationAndDeterministicOrdering() throws Exception {
    UUID endpoint = endpoint("paging");
    assertThat(json(list(endpoint)).get("content")).isEmpty();
    for (int i = 0; i < 3; i++) {
      post(endpoint, "https://receiver.example.com/item-" + i);
    }
    JsonNode defaults = json(list(endpoint));
    assertThat(defaults.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("page", "size", "totalElements", "totalPages", "content");
    assertThat(defaults.get("page").asInt()).isZero();
    assertThat(defaults.get("size").asInt()).isEqualTo(20);
    assertThat(defaults.get("totalElements").asInt()).isEqualTo(3);
    assertThat(json(list(endpoint, "?size=2")).get("content")).hasSize(2);
    assertThat(json(list(endpoint, "?page=1&size=2")).get("content")).hasSize(1);
    assertThat(json(list(endpoint, "?size=100")).get("size").asInt()).isEqualTo(100);
    for (String query : List.of("?page=-1", "?size=0", "?size=-1", "?size=101")) {
      ResponseEntity<String> r = list(endpoint, query);
      assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(json(r).get("code").asText()).isEqualTo("VALIDATION_ERROR");
    }
    UUID older = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID newer = UUID.fromString("00000000-0000-0000-0000-000000000002");
    jdbc.update(
        "INSERT INTO webhook_destinations(id, endpoint_id, url, created_at) VALUES (?,?,?,TIMESTAMPTZ '2020-01-01T00:00:00Z'), (?,?,?,TIMESTAMPTZ '2020-01-01T00:00:00Z')",
        older,
        endpoint,
        "https://receiver.example.com/tie-old",
        newer,
        endpoint,
        "https://receiver.example.com/tie-new");
    JsonNode tie = json(list(endpoint, "?size=100"));
    List<String> ids = new java.util.ArrayList<>();
    tie.get("content").forEach(n -> ids.add(n.get("id").asText()));
    assertThat(ids.indexOf(newer.toString())).isLessThan(ids.indexOf(older.toString()));
  }

  @Test
  void problemDetailsAreStableAndAuthenticationIsRequired() throws Exception {
    UUID endpoint = endpoint("errors");
    HttpHeaders missing = new HttpHeaders();
    ResponseEntity<String> unauthorized =
        client.exchange(
            url("/api/v1/endpoints/" + endpoint + "/destinations"),
            HttpMethod.GET,
            new HttpEntity<>(null, missing),
            String.class);
    assertProblem(unauthorized, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    HttpHeaders wrong = new HttpHeaders();
    wrong.set("X-HookScope-Admin-Token", "wrong-token");
    assertProblem(
        client.exchange(
            url("/api/v1/endpoints/" + endpoint + "/destinations"),
            HttpMethod.GET,
            new HttpEntity<>(null, wrong),
            String.class),
        HttpStatus.UNAUTHORIZED,
        "UNAUTHORIZED");
    assertProblem(list(UUID.randomUUID()), HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND");
  }

  @Test
  void literalNullDestinationBodyReturnsSanitizedValidationProblem() throws Exception {
    UUID endpoint = endpoint("null-body");
    ResponseEntity<String> response =
        client.exchange(
            url("/api/v1/endpoints/" + endpoint + "/destinations"),
            HttpMethod.POST,
            entity("null"),
            String.class);
    assertProblem(response, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
    assertThat(response.getBody()).doesNotContain("NullPointerException", "stackTrace");
  }

  private UUID endpoint(String name) {
    return UUID.fromString(
        json(client.exchange(
                url("/api/v1/endpoints"),
                HttpMethod.POST,
                entity("{\"name\":\"" + name + "\"}"),
                String.class))
            .get("id")
            .asText());
  }

  private ResponseEntity<String> post(UUID id, String value) {
    return client.exchange(
        url("/api/v1/endpoints/" + id + "/destinations"),
        HttpMethod.POST,
        entity("{\"url\":\"" + value + "\"}"),
        String.class);
  }

  private ResponseEntity<String> get(UUID e, UUID d) {
    return client.exchange(
        url("/api/v1/endpoints/" + e + "/destinations/" + d),
        HttpMethod.GET,
        entity(null),
        String.class);
  }

  private ResponseEntity<String> list(UUID e) {
    return list(e, "");
  }

  private ResponseEntity<String> list(UUID e, String query) {
    return client.exchange(
        url("/api/v1/endpoints/" + e + "/destinations" + query),
        HttpMethod.GET,
        entity(null),
        String.class);
  }

  private void assertColumn(String name, String type, String nullable) {
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT data_type, is_nullable FROM information_schema.columns WHERE table_name='webhook_destinations' AND column_name='"
                + name
                + "'");
    assertThat(row.get("data_type")).isEqualTo(type);
    assertThat(row.get("is_nullable")).isEqualTo(nullable);
  }

  private void assertProblem(ResponseEntity<String> response, HttpStatus status, String code)
      throws Exception {
    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    JsonNode body = json(response);
    assertThat(body.get("code").asText()).isEqualTo(code);
    assertThat(body.fieldNames())
        .toIterable()
        .contains("type", "title", "status", "detail", "instance", "code");
  }

  private HttpEntity<?> entity(Object body) {
    HttpHeaders h = new HttpHeaders();
    h.set("X-HookScope-Admin-Token", TOKEN);
    h.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, h);
  }

  private JsonNode json(ResponseEntity<String> r) {
    try {
      return mapper.readTree(r.getBody());
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }
}
