package io.hookscope;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith({SpringExtension.class, OutputCaptureExtension.class})
class EndpointManagementIntegrationTest {

  private static final String ADMIN_TOKEN = "isolated-integration-test-token-not-for-production";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Flyway flyway;
  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @LocalServerPort private int port;

  @BeforeEach
  void clearEndpoints() {
    jdbcTemplate.update("DELETE FROM webhook_endpoints");
  }

  @Test
  void migratesAnEmptyDatabaseWithTheLockedEndpointSchema() {
    flyway.validate();
    assertThat(flyway.info().applied()).hasSize(2);
    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT column_name || ':' || data_type || ':' || is_nullable || ':' ||
                    COALESCE(character_maximum_length::text, '') || ':' ||
                    CASE WHEN column_default IS NULL THEN 'no-default' ELSE 'default' END
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'webhook_endpoints'
                ORDER BY ordinal_position
                """,
                String.class))
        .containsExactly(
            "id:uuid:NO::no-default",
            "name:character varying:NO:120:no-default",
            "public_key:character varying:NO:32:no-default",
            "created_at:timestamp with time zone:NO::default");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema = 'public' AND table_name = 'webhook_endpoints'
                    AND constraint_type = 'PRIMARY KEY'
                """,
                String.class))
        .isEqualTo("webhook_endpoints_pkey");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT column_name FROM information_schema.key_column_usage
                WHERE table_schema = 'public' AND table_name = 'webhook_endpoints'
                    AND constraint_name = 'webhook_endpoints_pkey'
                """,
                String.class))
        .isEqualTo("id");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema = 'public' AND table_name = 'webhook_endpoints'
                    AND constraint_type = 'UNIQUE'
                """,
                String.class))
        .isEqualTo("webhook_endpoints_public_key_key");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'webhook_endpoints_created_at_id_desc_idx'
                """,
                String.class))
        .isEqualTo(
            "CREATE INDEX webhook_endpoints_created_at_id_desc_idx ON public.webhook_endpoints USING btree (created_at DESC, id DESC)");
  }

  @Test
  void createsAnEndpointWithTheLockedResponseContractAndRelativePath() throws Exception {
    ResponseEntity<String> response = create("  Payments sandbox  ", "attacker.example");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode body = json(response);
    assertThat(body.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("id", "name", "publicKey", "ingestionPath", "createdAt");
    assertThat(body.get("name").asText()).isEqualTo("Payments sandbox");
    assertThat(body.get("publicKey").asText()).matches("[A-Za-z0-9_-]{32}");
    assertThat(body.get("ingestionPath").asText())
        .isEqualTo("/hooks/" + body.get("publicKey").asText());
    assertThat(body.get("ingestionPath").asText()).doesNotContain("attacker.example");
    assertThat(UUID.fromString(body.get("id").asText())).isNotNull();
    assertThat(Instant.parse(body.get("createdAt").asText())).isNotNull();
  }

  @Test
  void acceptsOneAndOneHundredTwentyCharacterNamesAfterTrimming() throws Exception {
    ResponseEntity<String> oneCharacter = create("x", null);
    ResponseEntity<String> oneHundredTwentyCharacters = create(" " + "x".repeat(120) + " ", null);

    assertThat(oneCharacter.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(json(oneCharacter).get("name").asText()).hasSize(1);
    assertThat(oneHundredTwentyCharacters.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(json(oneHundredTwentyCharacters).get("name").asText()).hasSize(120);
  }

  @Test
  void trimsUnicodeWhitespaceAndRejectsUnicodeWhitespaceOnlyNames() throws Exception {
    ResponseEntity<String> trimmed = create("\u2003Payments\u2002", null);
    assertThat(trimmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(json(trimmed).get("name").asText()).isEqualTo("Payments");

    assertProblem(
        create("\u2003\u2002", null),
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "The endpoint name must contain between 1 and 120 characters after trimming.");
  }

  @ParameterizedTest
  @CsvSource({"'   '", "too-long"})
  void rejectsBlankAndOverlongNames(String kind) throws Exception {
    String name = kind.equals("too-long") ? "x".repeat(121) : kind;
    ResponseEntity<String> response = create(name, null);

    assertProblem(
        response,
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "The endpoint name must contain between 1 and 120 characters after trimming.");
  }

  @Test
  void retrievesAnEndpointAndReturnsNotFoundForAnUnknownUuid() throws Exception {
    JsonNode created = json(create("Retrieve me", null));

    ResponseEntity<String> found = get("/api/v1/endpoints/" + created.get("id").asText());
    assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(json(found).get("publicKey").asText()).isEqualTo(created.get("publicKey").asText());

    assertProblem(
        get("/api/v1/endpoints/00000000-0000-0000-0000-000000000000"),
        HttpStatus.NOT_FOUND,
        "ENDPOINT_NOT_FOUND");
  }

  @Test
  void listsEmptyDefaultMaximumAndPartialPages() throws Exception {
    JsonNode empty = json(get("/api/v1/endpoints"));
    assertPage(empty, 0, 20, 0, 0, 0);
    for (int index = 0; index < 25; index++) {
      insertEndpoint(
          UUID.randomUUID(), "Endpoint " + index, String.format("%032d", index), Instant.now());
    }

    assertPage(json(get("/api/v1/endpoints")), 0, 20, 25, 2, 20);
    assertPage(json(get("/api/v1/endpoints?page=1&size=20")), 1, 20, 25, 2, 5);
    assertPage(json(get("/api/v1/endpoints?size=100")), 0, 100, 25, 1, 25);
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "page=-1|The page must be zero or greater.",
        "size=0|The size must be at least 1.",
        "size=101|The size must not exceed 100."
      },
      delimiter = '|')
  void rejectsInvalidPagination(String query, String detail) throws Exception {
    assertProblem(
        get("/api/v1/endpoints?" + query), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail);
  }

  @Test
  void returnsMalformedRequestProblemsForMalformedUuidPaginationAndJson() throws Exception {
    assertProblem(get("/api/v1/endpoints/not-a-uuid"), HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
    assertProblem(
        get("/api/v1/endpoints?size=not-a-number"), HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
    ResponseEntity<String> malformedJson =
        restTemplate.exchange(
            url("/api/v1/endpoints"),
            HttpMethod.POST,
            new HttpEntity<>("{\"name\":", headers(ADMIN_TOKEN)),
            String.class);
    assertProblem(malformedJson, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
  }

  @Test
  void ordersTimestampTiesByDescendingId() throws Exception {
    Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
    UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID high = UUID.fromString("00000000-0000-0000-0000-000000000003");
    UUID middle = UUID.fromString("00000000-0000-0000-0000-000000000002");
    insertEndpoint(low, "low", "A".repeat(32), timestamp);
    insertEndpoint(high, "high", "B".repeat(32), timestamp);
    insertEndpoint(middle, "middle", "C".repeat(32), timestamp);

    JsonNode content = json(get("/api/v1/endpoints?size=100")).get("content");
    assertThat(content.get(0).get("id").asText()).isEqualTo(high.toString());
    assertThat(content.get(1).get("id").asText()).isEqualTo(middle.toString());
    assertThat(content.get(2).get("id").asText()).isEqualTo(low.toString());
  }

  @Test
  void databaseRejectsDuplicatePublicKeys() {
    insertEndpoint(UUID.randomUUID(), "First", "D".repeat(32), Instant.now());

    assertThat(
            org.assertj.core.api.Assertions.catchThrowable(
                () -> insertEndpoint(UUID.randomUUID(), "Second", "D".repeat(32), Instant.now())))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void protectsManagementRoutesButLeavesHealthPublic(CapturedOutput output) throws Exception {
    ResponseEntity<String> missing =
        restTemplate.getForEntity(url("/api/v1/endpoints"), String.class);
    assertProblem(missing, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    assertThat(missing.getBody()).doesNotContain(ADMIN_TOKEN);
    ResponseEntity<String> matrixPathMissing =
        restTemplate.getForEntity(url("/api/v1/endpoints;attempt=1"), String.class);
    assertProblem(matrixPathMissing, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    ResponseEntity<String> invalid =
        restTemplate.exchange(
            url("/api/v1/endpoints"), HttpMethod.GET, entity(null, "wrong-token"), String.class);
    assertProblem(invalid, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    assertThat(restTemplate.getForEntity(url("/actuator/health"), String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    JsonNode created = json(create("Private", null));
    assertThat(output.getAll())
        .doesNotContain(ADMIN_TOKEN)
        .doesNotContain(created.get("publicKey").asText());
  }

  private ResponseEntity<String> create(String name, String host) {
    HttpHeaders headers = headers(ADMIN_TOKEN);
    if (host != null) {
      headers.set(HttpHeaders.HOST, host);
    }
    return restTemplate.exchange(
        url("/api/v1/endpoints"),
        HttpMethod.POST,
        new HttpEntity<>(Map.of("name", name), headers),
        String.class);
  }

  private ResponseEntity<String> get(String path) {
    return restTemplate.exchange(
        url(path), HttpMethod.GET, entity(null, ADMIN_TOKEN), String.class);
  }

  private HttpEntity<?> entity(Object body, String token) {
    return new HttpEntity<>(body, headers(token));
  }

  private HttpHeaders headers(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-HookScope-Admin-Token", token);
    return headers;
  }

  private JsonNode json(ResponseEntity<String> response) throws Exception {
    return objectMapper.readTree(response.getBody());
  }

  private void assertProblem(ResponseEntity<String> response, HttpStatus status, String code)
      throws Exception {
    assertProblem(response, status, code, null);
  }

  private void assertProblem(
      ResponseEntity<String> response, HttpStatus status, String code, String expectedDetail)
      throws Exception {
    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    JsonNode problem = json(response);
    assertThat(problem.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("type", "title", "status", "detail", "instance", "code");
    assertThat(problem.get("type").asText())
        .isEqualTo("urn:hookscope:error:" + code.toLowerCase().replace('_', '-'));
    assertThat(problem.get("title").asText()).isNotBlank();
    assertThat(problem.get("status").asInt()).isEqualTo(status.value());
    assertThat(problem.get("detail").asText()).isNotBlank();
    if (expectedDetail != null) {
      assertThat(problem.get("detail").asText()).isEqualTo(expectedDetail);
    }
    assertThat(problem.get("instance").asText()).startsWith("/");
    assertThat(problem.get("code").asText()).isEqualTo(code);
  }

  private void assertPage(
      JsonNode page, int expectedPage, int expectedSize, long total, int pages, int contentSize) {
    assertThat(page.get("page").asInt()).isEqualTo(expectedPage);
    assertThat(page.get("size").asInt()).isEqualTo(expectedSize);
    assertThat(page.get("totalElements").asLong()).isEqualTo(total);
    assertThat(page.get("totalPages").asInt()).isEqualTo(pages);
    assertThat(page.get("content")).hasSize(contentSize);
  }

  private void insertEndpoint(UUID id, String name, String key, Instant createdAt) {
    jdbcTemplate.update(
        "INSERT INTO webhook_endpoints (id, name, public_key, created_at) VALUES (?, ?, ?, ?)",
        id,
        name,
        key,
        Timestamp.from(createdAt));
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }
}
