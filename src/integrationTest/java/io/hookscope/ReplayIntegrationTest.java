package io.hookscope;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.hookscope.delivery.DeliveryRetryQueue;
import io.hookscope.endpoint.WebhookDestination;
import io.hookscope.endpoint.WebhookDestinationRepository;
import io.hookscope.endpoint.WebhookEndpoint;
import io.hookscope.endpoint.WebhookEndpointRepository;
import io.hookscope.event.WebhookEvent;
import io.hookscope.event.WebhookEventRepository;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
@TestPropertySource(properties = "hookscope.delivery.allowed-hosts=127.0.0.1")
class ReplayIntegrationTest {
  private static final String ADMIN_TOKEN = "isolated-integration-test-token-not-for-production";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10");

  @Autowired private WebhookEndpointRepository endpoints;
  @Autowired private WebhookDestinationRepository destinations;
  @Autowired private WebhookEventRepository events;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TestRestTemplate rest;
  @Autowired private ObjectMapper json;
  @MockBean private DeliveryRetryQueue retries;
  @LocalServerPort private int port;
  private HttpServer receiver;

  @BeforeEach
  void clear() {
    jdbc.update("DELETE FROM webhook_delivery_attempts");
    jdbc.update("DELETE FROM webhook_deliveries");
    jdbc.update("DELETE FROM webhook_destinations");
    jdbc.update("DELETE FROM webhook_events");
    jdbc.update("DELETE FROM webhook_endpoints");
  }

  @AfterEach
  void stopReceiver() {
    if (receiver != null) {
      receiver.stop(0);
    }
  }

  @Test
  void replaysStoredRequestOnceToTheConfiguredDestinationAndPreservesTheOriginal()
      throws Exception {
    AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    String destinationUrl = startReceiver(204, captured);
    WebhookEndpoint endpoint = endpoint("Replay success");
    WebhookDestination destination = destination(endpoint, destinationUrl);
    byte[] originalBody = new byte[] {0, 1, -1, 42};
    WebhookEvent event = event(endpoint, "PATCH", "application/octet-stream", originalBody);
    Map<String, Object> before = original(event.getId());

    ResponseEntity<String> response = replay(event.getId(), destination.getId());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode body = json.readTree(response.getBody());
    UUID deliveryId = UUID.fromString(body.get("id").asText());
    assertThat(captured.get().method()).isEqualTo("PATCH");
    assertThat(captured.get().body()).isEqualTo(originalBody);
    assertThat(captured.get().contentType()).isEqualTo("application/octet-stream");
    assertOriginalUnchanged(event.getId(), before);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM webhook_deliveries WHERE event_id = ?",
                Integer.class,
                event.getId()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT kind || ':' || status FROM webhook_deliveries WHERE id = ?",
                String.class,
                deliveryId))
        .isEqualTo("REPLAY:SUCCEEDED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM webhook_delivery_attempts WHERE delivery_id = ?",
                Integer.class,
                deliveryId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForMap(
                "SELECT attempt_number,http_status,error_code FROM webhook_delivery_attempts WHERE delivery_id = ?",
                deliveryId))
        .containsEntry("attempt_number", 1)
        .containsEntry("http_status", 204)
        .containsEntry("error_code", null);
  }

  @Test
  void recordsAFailedSingleReplayAttemptForAnHttp500Response() throws Exception {
    String destinationUrl = startReceiver(500, new AtomicReference<>());
    WebhookEndpoint endpoint = endpoint("Replay failure");
    WebhookDestination destination = destination(endpoint, destinationUrl);
    WebhookEvent event =
        event(endpoint, "POST", "text/plain", "unchanged".getBytes(StandardCharsets.UTF_8));
    Map<String, Object> before = original(event.getId());

    ResponseEntity<String> response = replay(event.getId(), destination.getId());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertOriginalUnchanged(event.getId(), before);
    UUID deliveryId = UUID.fromString(json.readTree(response.getBody()).get("id").asText());
    assertThat(
            jdbc.queryForObject(
                "SELECT kind || ':' || status FROM webhook_deliveries WHERE id = ?",
                String.class,
                deliveryId))
        .isEqualTo("REPLAY:FAILED");
    assertThat(
            jdbc.queryForMap(
                "SELECT attempt_number,http_status,error_code FROM webhook_delivery_attempts WHERE delivery_id = ?",
                deliveryId))
        .containsEntry("attempt_number", 1)
        .containsEntry("http_status", 500)
        .containsEntry("error_code", null);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM webhook_deliveries WHERE event_id = ?",
                Integer.class,
                event.getId()))
        .isEqualTo(1);
  }

  @Test
  void rejectsUnknownOrCrossEndpointReplayTargetsWithoutCreatingDeliveries() throws Exception {
    WebhookEndpoint endpoint = endpoint("Replay validation");
    WebhookDestination destination = destination(endpoint, "http://127.0.0.1:1/unused");
    WebhookEvent event = event(endpoint, "POST", null, new byte[0]);
    WebhookEndpoint otherEndpoint = endpoint("Other endpoint");
    WebhookDestination otherDestination = destination(otherEndpoint, "http://127.0.0.1:2/unused");

    assertNotFound(replay(UUID.randomUUID(), destination.getId()));
    assertNotFound(replay(event.getId(), UUID.randomUUID()));
    assertNotFound(replay(event.getId(), otherDestination.getId()));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_deliveries", Integer.class))
        .isZero();
  }

  private String startReceiver(int status, AtomicReference<CapturedRequest> captured)
      throws Exception {
    receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    receiver.createContext(
        "/receive",
        exchange -> {
          captured.set(
              new CapturedRequest(
                  exchange.getRequestMethod(),
                  exchange.getRequestBody().readAllBytes(),
                  exchange.getRequestHeaders().getFirst("Content-Type")));
          exchange.sendResponseHeaders(status, -1);
          exchange.close();
        });
    receiver.start();
    return "http://127.0.0.1:" + receiver.getAddress().getPort() + "/receive";
  }

  private WebhookEndpoint endpoint(String name) {
    return endpoints.save(
        new WebhookEndpoint(
            UUID.randomUUID(), name, UUID.randomUUID().toString().replace("-", ""), Instant.now()));
  }

  private WebhookDestination destination(WebhookEndpoint endpoint, String url) {
    return destinations.save(
        new WebhookDestination(UUID.randomUUID(), endpoint.getId(), url, Instant.now()));
  }

  private WebhookEvent event(
      WebhookEndpoint endpoint, String method, String contentType, byte[] body) {
    return events.save(
        new WebhookEvent(
            UUID.randomUUID(),
            endpoint.getId(),
            method,
            Map.of("x-test", List.of("original")),
            Map.of("trace", List.of("one")),
            contentType,
            body,
            body.length,
            "a".repeat(64),
            "127.0.0.1",
            "/hooks/" + endpoint.getPublicKey(),
            Instant.parse("2026-02-01T00:00:00Z")));
  }

  private Map<String, Object> original(UUID eventId) {
    return jdbc.queryForMap(
        "SELECT method,content_type,body,body_size,body_sha256,headers,query_parameters,path,received_at FROM webhook_events WHERE id = ?",
        eventId);
  }

  private void assertOriginalUnchanged(UUID eventId, Map<String, Object> before) {
    Map<String, Object> after = new java.util.LinkedHashMap<>(original(eventId));
    byte[] beforeBody = (byte[]) before.remove("body");
    byte[] afterBody = (byte[]) after.remove("body");
    assertThat(after).isEqualTo(before);
    assertThat(afterBody).isEqualTo(beforeBody);
  }

  private ResponseEntity<String> replay(UUID eventId, UUID destinationId) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-HookScope-Admin-Token", ADMIN_TOKEN);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return rest.exchange(
        url("/api/v1/events/" + eventId + "/replay"),
        HttpMethod.POST,
        new HttpEntity<>("{\"destinationId\":\"" + destinationId + "\"}", headers),
        String.class);
  }

  private void assertNotFound(ResponseEntity<String> response) throws Exception {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    JsonNode problem = json.readTree(response.getBody());
    assertThat(problem.get("code").asText()).isEqualTo("EVENT_NOT_FOUND");
    assertThat(problem.get("detail").asText())
        .doesNotContain("destination", "database", "constraint");
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private record CapturedRequest(String method, byte[] body, String contentType) {}
}
