package io.hookscope;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hookscope.event.EventListProjection;
import io.hookscope.event.EventService;
import io.hookscope.event.WebhookEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith(OutputCaptureExtension.class)
class IngestionIntegrationTest {
  private static final String ADMIN_TOKEN = "isolated-integration-test-token-not-for-production";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private TestRestTemplate rest;
  @Autowired private ObjectMapper json;
  @Autowired private EventService eventService;
  @LocalServerPort private int port;
  private UUID endpointId;
  private String publicKey;

  @BeforeEach
  void setup() {
    jdbc.update("DELETE FROM webhook_events");
    jdbc.update("DELETE FROM webhook_endpoints");
    endpointId = UUID.randomUUID();
    publicKey = "K".repeat(32);
    jdbc.update(
        "INSERT INTO webhook_endpoints (id,name,public_key,created_at) VALUES (?,?,?,?)",
        endpointId,
        "Ingestion",
        publicKey,
        java.sql.Timestamp.from(Instant.now()));
  }

  @Test
  void migrationUsesTheLockedEventSchema() {
    Map<String, String> types =
        jdbc.query(
            "SELECT a.attname, format_type(a.atttypid, a.atttypmod) "
                + "FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid "
                + "WHERE c.relname='webhook_events' AND a.attnum > 0 AND NOT a.attisdropped",
            resultSet -> {
              Map<String, String> values = new LinkedHashMap<>();
              while (resultSet.next()) {
                values.put(resultSet.getString(1), resultSet.getString(2));
              }
              return values;
            });
    assertThat(types)
        .containsExactlyInAnyOrderEntriesOf(
            Map.ofEntries(
                Map.entry("id", "uuid"),
                Map.entry("endpoint_id", "uuid"),
                Map.entry("method", "character varying(10)"),
                Map.entry("headers", "jsonb"),
                Map.entry("query_parameters", "jsonb"),
                Map.entry("content_type", "character varying(255)"),
                Map.entry("body", "bytea"),
                Map.entry("body_size", "bigint"),
                Map.entry("body_sha256", "character(64)"),
                Map.entry("source_ip", "inet"),
                Map.entry("path", "character varying(1024)"),
                Map.entry("received_at", "timestamp with time zone")));
    Map<String, String> nullability =
        jdbc.query(
            "SELECT column_name,is_nullable FROM information_schema.columns "
                + "WHERE table_name='webhook_events'",
            resultSet -> {
              Map<String, String> values = new LinkedHashMap<>();
              while (resultSet.next()) {
                values.put(resultSet.getString(1), resultSet.getString(2));
              }
              return values;
            });
    assertThat(nullability).containsEntry("content_type", "YES");
    assertThat(
            nullability.entrySet().stream().filter(entry -> !entry.getKey().equals("content_type")))
        .allMatch(entry -> entry.getValue().equals("NO"));
    assertThat(
            jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_name='webhook_events' AND column_name='source_ip'",
                String.class))
        .isEqualTo("inet");
    assertThat(
            jdbc.queryForObject(
                "SELECT udt_name FROM information_schema.columns WHERE table_name='webhook_events' AND column_name='source_ip'",
                String.class))
        .isEqualTo("inet");
    assertThat(
            jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_name='webhook_events' AND column_name='received_at'",
                String.class))
        .isEqualTo("timestamp with time zone");
    assertThat(
            jdbc.queryForObject(
                "SELECT column_default FROM information_schema.columns WHERE table_name='webhook_events' AND column_name='received_at'",
                String.class))
        .containsIgnoringCase("current_timestamp");
    assertThat(
            jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name='webhook_events' AND column_name='content_type'",
                String.class))
        .isEqualTo("YES");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.check_constraints WHERE constraint_name IN ('webhook_events_body_size_nonnegative','webhook_events_body_sha256_format')",
                Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT delete_rule FROM information_schema.referential_constraints WHERE constraint_name='webhook_events_endpoint_id_fkey'",
                String.class))
        .isEqualTo("NO ACTION");
    assertThat(
            jdbc.queryForList(
                "SELECT a.attname FROM pg_index i "
                    + "JOIN pg_class c ON c.oid=i.indrelid "
                    + "JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum=ANY(i.indkey) "
                    + "WHERE c.relname='webhook_events' AND i.indisprimary",
                String.class))
        .containsExactly("id");
    assertThat(
            jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname='webhook_events_endpoint_id_fkey'",
                String.class))
        .isEqualTo("FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoints(id)");
    assertThat(
            jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname='webhook_events_body_size_nonnegative'",
                String.class))
        .contains("body_size >= 0");
    assertThat(
            jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                    + "WHERE conname='webhook_events_body_sha256_format'",
                String.class))
        .contains("^[0-9a-f]{64}$");
    assertThat(
            jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname='webhook_events_endpoint_id_received_at_id_desc_idx'",
                String.class))
        .isEqualTo(
            "CREATE INDEX webhook_events_endpoint_id_received_at_id_desc_idx ON public.webhook_events USING btree (endpoint_id, received_at DESC, id DESC)");
  }

  @Test
  void publiclyPersistsAndReturnsAnExactRedactedDetail() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    String authorizationValue = "runtime-sensitive-" + UUID.randomUUID();
    headers.add("Authorization", authorizationValue);
    headers.add("X-Trace", "one");
    headers.add("X-Trace", "two");
    ResponseEntity<String> ingest =
        rest.exchange(
            url("/hooks/" + publicKey + "?Case=One&Case=Two"),
            HttpMethod.POST,
            new HttpEntity<>(new byte[] {0, 1, 2, 3}, headers),
            String.class);
    assertThat(ingest.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    UUID eventId = jdbc.queryForObject("SELECT id FROM webhook_events", UUID.class);
    ResponseEntity<String> detail =
        rest.exchange(
            url("/api/v1/endpoints/" + endpointId + "/events/" + eventId),
            HttpMethod.GET,
            adminEntity(),
            String.class);
    assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = json.readTree(detail.getBody());
    assertThat(body.get("bodyBase64").asText()).isEqualTo("AAECAw==");
    assertThat(body.get("bodySize").asLong()).isEqualTo(4);
    assertThat(body.get("headers").get("authorization").get(0).asText()).isEqualTo("[REDACTED]");
    assertThat(jdbc.queryForObject("SELECT headers::text FROM webhook_events", String.class))
        .doesNotContain(authorizationValue);
    assertThat(body.get("headers").get("x-trace")).hasSize(2);
    assertThat(body.get("queryParameters").get("Case")).hasSize(2);
    assertThat(body.get("bodySha256").asText()).matches("[0-9a-f]{64}");
    assertThat(body.get("bodySha256").asText()).isEqualTo(sha256(new byte[] {0, 1, 2, 3}));
    assertThat(body.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "id",
            "method",
            "path",
            "contentType",
            "bodySize",
            "sourceIp",
            "receivedAt",
            "headers",
            "queryParameters",
            "bodyBase64",
            "bodySha256");
  }

  @Test
  void redactsEveryDefaultSensitiveHeaderBeforePersistence() {
    HttpHeaders headers = new HttpHeaders();
    String[] sensitive = {
      "Authorization",
      "Proxy-Authorization",
      "Cookie",
      "Set-Cookie",
      "X-Api-Key",
      "Api-Key",
      "X-Auth-Token"
    };
    for (String name : sensitive) {
      headers.add(name, "must-not-persist-" + name);
    }
    headers.add("X-Signature", "signature-must-remain");
    rest.exchange(
        url("/hooks/" + publicKey),
        HttpMethod.POST,
        new HttpEntity<>(new byte[0], headers),
        String.class);
    String stored = jdbc.queryForObject("SELECT headers::text FROM webhook_events", String.class);
    for (String name : sensitive) {
      assertThat(stored).doesNotContain("must-not-persist-" + name);
    }
    assertThat(stored).contains("[REDACTED]").contains("signature-must-remain");
  }

  @Test
  void acceptsTheExactDefaultBodyLimit() {
    byte[] body = new byte[1_048_576];
    ResponseEntity<String> response =
        rest.exchange(
            url("/hooks/" + publicKey), HttpMethod.POST, new HttpEntity<>(body), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(jdbc.queryForObject("SELECT octet_length(body) FROM webhook_events", Integer.class))
        .isEqualTo(body.length);
    assertThat(jdbc.queryForObject("SELECT body_size FROM webhook_events", Long.class))
        .isEqualTo((long) body.length);
  }

  @Test
  void storesANullContentTypeWhenTheRequestHasNone() {
    ResponseEntity<String> response =
        rest.exchange(
            url("/hooks/" + publicKey), HttpMethod.GET, new HttpEntity<>(null), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(jdbc.queryForObject("SELECT content_type FROM webhook_events", String.class))
        .isNull();
  }

  @ParameterizedTest
  @MethodSource("supportedMethods")
  void supportsEveryLockedIngestionMethod(HttpMethod method) {
    ResponseEntity<String> response =
        rest.exchange(
            url("/hooks/" + publicKey), method, new HttpEntity<>(new byte[0]), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(jdbc.queryForObject("SELECT method FROM webhook_events", String.class))
        .isEqualTo(method.name());
  }

  private static java.util.stream.Stream<HttpMethod> supportedMethods() {
    return java.util.stream.Stream.of(
        HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
  }

  @Test
  void rejectsUnknownKeysAndUnsupportedMethodsWithProblemDetails() throws Exception {
    ResponseEntity<String> unknown =
        rest.exchange(
            url("/hooks/" + "Z".repeat(32)),
            HttpMethod.POST,
            new HttpEntity<>(new byte[0]),
            String.class);
    assertHookProblem(unknown, HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND", "Z".repeat(32));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_events", Integer.class)).isZero();
    ResponseEntity<String> options =
        rest.exchange(
            url("/hooks/" + publicKey), HttpMethod.OPTIONS, new HttpEntity<>(null), String.class);
    assertHookProblem(options, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", publicKey);
    ResponseEntity<String> head =
        rest.exchange(
            url("/hooks/" + publicKey), HttpMethod.HEAD, new HttpEntity<>(null), String.class);
    assertThat(head.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(head.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(head.getBody()).isNull();
  }

  @Test
  void rejectsBodiesOverTheConfiguredLimitWithoutPersistence() throws Exception {
    ResponseEntity<String> response =
        rest.exchange(
            url("/hooks/" + publicKey),
            HttpMethod.POST,
            new HttpEntity<>(new byte[1_048_577]),
            String.class);
    assertHookProblem(response, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", publicKey);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_events", Integer.class)).isZero();
  }

  @Test
  void rejectsChunkedUnknownLengthBodiesOverTheLimitWithoutPersistenceAndRemainsHealthy()
      throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      OutputStream output = socket.getOutputStream();
      output.write(
          ("POST /hooks/"
                  + publicKey
                  + " HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n")
              .getBytes(StandardCharsets.US_ASCII));
      output.write("100001\r\n".getBytes(StandardCharsets.US_ASCII));
      output.write(new byte[1_048_577]);
      output.write("\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
      output.flush();
      String status =
          new BufferedReader(
                  new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
              .readLine();
      assertThat(status).contains("413");
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_events", Integer.class)).isZero();
    assertThat(rest.getForEntity(url("/actuator/health"), String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void traceIsRejectedBeforeIngestionAndCreatesNoEvent() throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      OutputStream output = socket.getOutputStream();
      output.write(
          ("TRACE /hooks/" + publicKey + " HTTP/1.1\r\nHost: localhost\r\n\r\n")
              .getBytes(StandardCharsets.US_ASCII));
      output.flush();
      String status =
          new BufferedReader(
                  new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
              .readLine();
      assertThat(status).containsAnyOf("405", "400");
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_events", Integer.class)).isZero();
  }

  @Test
  void connectIsRejectedByTheContainerBeforeIngestion() throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      OutputStream output = socket.getOutputStream();
      output.write(
          ("CONNECT /hooks/" + publicKey + " HTTP/1.1\r\nHost: localhost\r\n\r\n")
              .getBytes(StandardCharsets.US_ASCII));
      output.flush();
      String status =
          new BufferedReader(
                  new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
              .readLine();
      assertThat(status).contains("501");
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_events", Integer.class)).isZero();
  }

  @Test
  void problemDetailsAndCapturedOutputRemainSanitized(CapturedOutput output) throws Exception {
    ResponseEntity<String> unauthorized =
        rest.getForEntity(url("/api/v1/endpoints/" + endpointId + "/events"), String.class);
    assertProblem(unauthorized, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    ResponseEntity<String> malformed =
        rest.exchange(
            url("/api/v1/endpoints/not-a-uuid/events"),
            HttpMethod.GET,
            adminEntity(),
            String.class);
    assertProblem(malformed, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
    String bodyMarker = "runtime-body-" + UUID.randomUUID();
    String secretMarker = "runtime-sensitive-" + UUID.randomUUID();
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", secretMarker);
    rest.exchange(
        url("/hooks/" + publicKey),
        HttpMethod.POST,
        new HttpEntity<>(bodyMarker.getBytes(StandardCharsets.UTF_8), headers),
        String.class);
    assertThat(output.getAll())
        .doesNotContain(ADMIN_TOKEN)
        .doesNotContain(publicKey)
        .doesNotContain("/hooks/" + publicKey)
        .doesNotContain(bodyMarker)
        .doesNotContain(secretMarker);
  }

  @Test
  void capturesOnlyUrlQueryParametersAndPreservesTheRawFormBody() throws Exception {
    byte[] formBody =
        "bodyField=must-not-be-query&Case=body-value".getBytes(StandardCharsets.UTF_8);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    ResponseEntity<String> ingest =
        rest.exchange(
            URI.create(
                url("/hooks/" + publicKey + "?Case=One&Case=Two&%4Dix=hello%20world&bare&empty=")),
            HttpMethod.POST,
            new HttpEntity<>(formBody, headers),
            String.class);
    assertThat(ingest.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    UUID eventId = jdbc.queryForObject("SELECT id FROM webhook_events", UUID.class);
    JsonNode detail =
        json.readTree(
            rest.exchange(
                    url("/api/v1/endpoints/" + endpointId + "/events/" + eventId),
                    HttpMethod.GET,
                    adminEntity(),
                    String.class)
                .getBody());
    assertThat(detail.get("queryParameters").get("Case").get(0).asText()).isEqualTo("One");
    assertThat(detail.get("queryParameters").get("Case").get(1).asText()).isEqualTo("Two");
    assertThat(detail.get("queryParameters").get("Mix").get(0).asText()).isEqualTo("hello world");
    assertThat(detail.get("queryParameters").get("bare").get(0).asText()).isEmpty();
    assertThat(detail.get("queryParameters").get("empty").get(0).asText()).isEmpty();
    assertThat(detail.get("queryParameters").has("bodyField")).isFalse();
    assertThat(detail.get("bodyBase64").asText())
        .isEqualTo(java.util.Base64.getEncoder().encodeToString(formBody));
    assertThat(detail.get("bodySha256").asText()).isEqualTo(sha256(formBody));
  }

  @Test
  void rejectsMalformedRawQueryEncodingAndEncodedNulWithoutPersistence() throws Exception {
    assertMalformedRawQuery("bad=%ZZ");
    assertMalformedRawQuery("nul=%00");
    assertThat(rest.getForEntity(url("/actuator/health"), String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void storesDirectPeerAddressAndServerGeneratedTimestamp() throws Exception {
    Instant before = Instant.now();
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Forwarded-For", "198.51.100.10");
    headers.add("Forwarded", "for=198.51.100.11");
    headers.add("X-Real-IP", "198.51.100.12");
    rest.exchange(
        url("/hooks/" + publicKey),
        HttpMethod.POST,
        new HttpEntity<>(new byte[0], headers),
        String.class);
    Instant after = Instant.now();
    UUID eventId = jdbc.queryForObject("SELECT id FROM webhook_events", UUID.class);
    JsonNode detail =
        json.readTree(
            rest.exchange(
                    url("/api/v1/endpoints/" + endpointId + "/events/" + eventId),
                    HttpMethod.GET,
                    adminEntity(),
                    String.class)
                .getBody());
    String sourceIp = detail.get("sourceIp").asText();
    assertThat(sourceIp).isEqualTo("127.0.0.1");
    assertThat(sourceIp).isNotIn("198.51.100.10", "198.51.100.11", "198.51.100.12");
    Instant receivedAt = Instant.parse(detail.get("receivedAt").asText());
    assertThat(receivedAt).isBetween(before, after);
    assertThat(
            jdbc.queryForObject("SELECT received_at FROM webhook_events", Timestamp.class)
                .toInstant())
        .isEqualTo(receivedAt);
  }

  @Test
  void listsEventsWithLockedFieldsPaginationAndDeterministicTieOrdering() throws Exception {
    Instant tie = Instant.parse("2026-01-01T00:00:00Z");
    UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID high = UUID.fromString("00000000-0000-0000-0000-000000000003");
    insertEvent(low, tie);
    insertEvent(high, tie);
    ResponseEntity<String> empty =
        rest.exchange(
            url("/api/v1/endpoints/" + endpointId + "/events?page=1"),
            HttpMethod.GET,
            adminEntity(),
            String.class);
    JsonNode page =
        json.readTree(
            rest.exchange(
                    url("/api/v1/endpoints/" + endpointId + "/events?size=100"),
                    HttpMethod.GET,
                    adminEntity(),
                    String.class)
                .getBody());
    assertThat(page.get("content").get(0).get("id").asText()).isEqualTo(high.toString());
    assertThat(page.get("content").get(0).fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "id", "method", "path", "contentType", "bodySize", "sourceIp", "receivedAt");
    assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertProblem(
        rest.exchange(
            url("/api/v1/endpoints/" + endpointId + "/events?size=0"),
            HttpMethod.GET,
            adminEntity(),
            String.class),
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR");
    assertProblem(
        rest.exchange(
            url("/api/v1/endpoints/" + endpointId + "/events/00000000-0000-0000-0000-000000000000"),
            HttpMethod.GET,
            adminEntity(),
            String.class),
        HttpStatus.NOT_FOUND,
        "EVENT_NOT_FOUND");
  }

  @Test
  void listsSummaryProjectionsWithoutHydratingWebhookEventEntities() {
    insertEvent(UUID.randomUUID(), Instant.now());
    java.util.List<EventListProjection> summaries =
        eventService.list(endpointId, 0, 20).getContent();
    assertThat(summaries).hasSize(1);
    assertThat(summaries.getFirst()).isNotInstanceOf(WebhookEvent.class);
    assertThat(summaries.getFirst().getBodySize()).isZero();
  }

  @Test
  void handlesEmptyDefaultMaximumAndPartialEventPages() throws Exception {
    JsonNode empty =
        json.readTree(
            rest.exchange(
                    url("/api/v1/endpoints/" + endpointId + "/events"),
                    HttpMethod.GET,
                    adminEntity(),
                    String.class)
                .getBody());
    assertThat(empty.get("page").asInt()).isZero();
    assertThat(empty.get("size").asInt()).isEqualTo(20);
    assertThat(empty.get("totalElements").asLong()).isZero();
    for (int index = 0; index < 25; index++) {
      insertEvent(UUID.randomUUID(), Instant.now().plusSeconds(index));
    }
    JsonNode first =
        json.readTree(
            rest.exchange(
                    url("/api/v1/endpoints/" + endpointId + "/events"),
                    HttpMethod.GET,
                    adminEntity(),
                    String.class)
                .getBody());
    JsonNode last =
        json.readTree(
            rest.exchange(
                    url("/api/v1/endpoints/" + endpointId + "/events?page=1&size=20"),
                    HttpMethod.GET,
                    adminEntity(),
                    String.class)
                .getBody());
    JsonNode maximum =
        json.readTree(
            rest.exchange(
                    url("/api/v1/endpoints/" + endpointId + "/events?size=100"),
                    HttpMethod.GET,
                    adminEntity(),
                    String.class)
                .getBody());
    assertThat(first.get("content")).hasSize(20);
    assertThat(last.get("content")).hasSize(5);
    assertThat(last.get("totalPages").asInt()).isEqualTo(2);
    assertThat(maximum.get("content")).hasSize(25);
    assertProblem(
        rest.exchange(
            url("/api/v1/endpoints/" + endpointId + "/events?page=-1"),
            HttpMethod.GET,
            adminEntity(),
            String.class),
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR");
    assertProblem(
        rest.exchange(
            url("/api/v1/endpoints/" + endpointId + "/events?size=101"),
            HttpMethod.GET,
            adminEntity(),
            String.class),
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR");
  }

  private void insertEvent(UUID id, Instant receivedAt) {
    jdbc.update(
        "INSERT INTO webhook_events (id,endpoint_id,method,headers,query_parameters,body,body_size,body_sha256,source_ip,path,received_at) VALUES (?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,CAST(? AS inet),?,?)",
        id,
        endpointId,
        "POST",
        "{}",
        "{}",
        new byte[0],
        0L,
        "0".repeat(64),
        "127.0.0.1",
        "/hooks/" + publicKey,
        Timestamp.from(receivedAt));
  }

  private void assertMalformedRawQuery(String rawQuery) throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      OutputStream output = socket.getOutputStream();
      output.write(
          ("POST /hooks/"
                  + publicKey
                  + "?"
                  + rawQuery
                  + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
              .getBytes(StandardCharsets.US_ASCII));
      output.flush();
      String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertThat(response).contains("HTTP/1.1 400").contains("application/problem+json");
      int headerEnd = response.indexOf("\r\n\r\n");
      String headers = response.substring(0, headerEnd);
      String body = response.substring(headerEnd + 4);
      if (headers.contains("Transfer-Encoding: chunked")) {
        body = decodeChunkedBody(body);
      }
      JsonNode problem = json.readTree(body);
      assertThat(problem.fieldNames())
          .toIterable()
          .containsExactlyInAnyOrder("type", "title", "status", "detail", "instance", "code");
      assertThat(problem.get("code").asText()).isEqualTo("MALFORMED_REQUEST");
      assertThat(problem.get("instance").asText()).isEqualTo("/hooks");
      assertThat(body)
          .doesNotContain(publicKey)
          .doesNotContain("/hooks/" + publicKey)
          .doesNotContain(rawQuery);
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_events", Integer.class)).isZero();
  }

  private String decodeChunkedBody(String chunked) {
    StringBuilder body = new StringBuilder();
    int position = 0;
    while (true) {
      int lineEnd = chunked.indexOf("\r\n", position);
      int length = Integer.parseInt(chunked.substring(position, lineEnd), 16);
      if (length == 0) {
        return body.toString();
      }
      position = lineEnd + 2;
      body.append(chunked, position, position + length);
      position += length + 2;
    }
  }

  private String sha256(byte[] value) throws Exception {
    return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }

  private HttpEntity<Void> adminEntity() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-HookScope-Admin-Token", ADMIN_TOKEN);
    return new HttpEntity<>(headers);
  }

  private void assertProblem(ResponseEntity<String> response, HttpStatus status, String code)
      throws Exception {
    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    JsonNode problem = json.readTree(response.getBody());
    assertThat(problem.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("type", "title", "status", "detail", "instance", "code");
    assertThat(problem.get("code").asText()).isEqualTo(code);
  }

  private void assertHookProblem(
      ResponseEntity<String> response, HttpStatus status, String code, String suppliedKey)
      throws Exception {
    assertProblem(response, status, code);
    assertThat(response.getBody())
        .doesNotContain(suppliedKey)
        .doesNotContain("/hooks/" + suppliedKey);
    assertThat(json.readTree(response.getBody()).get("instance").asText()).isEqualTo("/hooks");
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }
}
