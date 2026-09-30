package io.hookscope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.hookscope.delivery.InitialDeliveryOrchestrator;
import io.hookscope.delivery.OutboundDeliveryClient;
import io.hookscope.delivery.WebhookDelivery;
import io.hookscope.delivery.WebhookDeliveryAttemptRepository;
import io.hookscope.delivery.WebhookDeliveryRepository;
import io.hookscope.endpoint.WebhookDestination;
import io.hookscope.endpoint.WebhookDestinationRepository;
import io.hookscope.endpoint.WebhookEndpoint;
import io.hookscope.endpoint.WebhookEndpointRepository;
import io.hookscope.event.WebhookEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeliveryOrchestrationIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10");

  @Autowired WebhookEndpointRepository endpoints;
  @Autowired WebhookDestinationRepository destinations;
  @Autowired io.hookscope.event.WebhookEventRepository events;
  @Autowired WebhookDeliveryRepository deliveries;
  @Autowired WebhookDeliveryAttemptRepository attempts;
  @Autowired InitialDeliveryOrchestrator orchestrator;
  @MockBean OutboundDeliveryClient client;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;

  @BeforeEach
  void clear() {
    jdbc.update("delete from webhook_delivery_attempts");
    jdbc.update("delete from webhook_deliveries");
    jdbc.update("delete from webhook_destinations");
    jdbc.update("delete from webhook_events");
    jdbc.update("delete from webhook_endpoints");
  }

  @Autowired TestRestTemplate http;

  @Test
  void createsOneDeliveryAndOneAttemptPerDestinationAndIsolatesFailure() {
    var endpoint =
        endpoints.save(
            new WebhookEndpoint(UUID.randomUUID(), "e", "abcdefghijklmnopqrstuvwx", Instant.now()));
    var d1 =
        destinations.save(
            new WebhookDestination(
                UUID.randomUUID(), endpoint.getId(), "http://one.invalid", Instant.now()));
    var d2 =
        destinations.save(
            new WebhookDestination(
                UUID.randomUUID(), endpoint.getId(), "http://two.invalid", Instant.now()));
    var d3 =
        destinations.save(
            new WebhookDestination(
                UUID.randomUUID(), endpoint.getId(), "http://three.invalid", Instant.now()));
    var event =
        events.save(
            new WebhookEvent(
                UUID.randomUUID(),
                endpoint.getId(),
                "POST",
                Map.of(),
                Map.of(),
                null,
                new byte[] {1},
                1,
                "a".repeat(64),
                "127.0.0.1",
                "/hooks/x",
                Instant.now()));
    when(client.send(
            org.mockito.ArgumentMatchers.eq(event),
            org.mockito.ArgumentMatchers.eq(d1.getUrl()),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              var delivery = invocation.getArgument(2, WebhookDelivery.class);
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              try (var connection = dataSource.getConnection();
                  var query =
                      connection.prepareStatement(
                          "select status, completed_at from webhook_deliveries where id = ?")) {
                query.setObject(1, delivery.getId());
                try (var result = query.executeQuery()) {
                  assertThat(result.next()).isTrue();
                  assertThat(result.getString(1)).isEqualTo("PENDING");
                  assertThat(result.getObject(2)).isNull();
                }
              }
              assertThat(
                      jdbc.queryForObject(
                          "select count(*) from webhook_events where id = ?",
                          Integer.class,
                          event.getId()))
                  .isEqualTo(1);
              assertThat(
                      jdbc.queryForObject(
                          "select status from webhook_deliveries where id = ?",
                          String.class,
                          delivery.getId()))
                  .isEqualTo("PENDING");
              assertThat(
                      jdbc.queryForObject(
                          "select count(*) from webhook_delivery_attempts where delivery_id = ?",
                          Integer.class,
                          delivery.getId()))
                  .isZero();
              return OutboundDeliveryClient.Result.failure("CONNECT_ERROR");
            });
    when(client.send(
            org.mockito.ArgumentMatchers.eq(event),
            org.mockito.ArgumentMatchers.eq(d2.getUrl()),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(OutboundDeliveryClient.Result.http(204));
    when(client.send(
            org.mockito.ArgumentMatchers.eq(event),
            org.mockito.ArgumentMatchers.eq(d3.getUrl()),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(OutboundDeliveryClient.Result.http(204));
    orchestrator.deliver(event);
    var rows =
        deliveries.findByEventId(
            event.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
    assertThat(rows.getContent())
        .hasSize(3)
        .allMatch(d -> d.getStatus().equals("FAILED") || d.getStatus().equals("SUCCEEDED"));
    assertThat(attempts.count()).isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from webhook_delivery_attempts where attempt_number > 1",
                Integer.class))
        .isZero();
  }

  @Test
  void realWebhookHttpPathPersistsEventBeforeControlledDelivery() {
    var endpoint =
        endpoints.save(
            new WebhookEndpoint(
                UUID.randomUUID(), "http", "abcdefghijklmnopqrstuvwx", Instant.now()));
    var destination =
        destinations.save(
            new WebhookDestination(
                UUID.randomUUID(), endpoint.getId(), "http://one.invalid", Instant.now()));
    var expected = OutboundDeliveryClient.Result.http(202);
    when(client.send(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(destination.getUrl()),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(expected);
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.TEXT_PLAIN);
    var response =
        http.postForEntity(
            "/hooks/" + endpoint.getPublicKey(), new HttpEntity<>("body", headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var eventId =
        jdbc.queryForObject(
            "select id from webhook_events where endpoint_id = ? order by received_at desc limit 1",
            UUID.class,
            endpoint.getId());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from webhook_deliveries where event_id = ? and status = 'SUCCEEDED'",
                Integer.class,
                eventId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from webhook_delivery_attempts where delivery_id in (select id from webhook_deliveries where event_id = ?)",
                Integer.class,
                eventId))
        .isEqualTo(1);
  }

  @Test
  void realWebhookHttpPathSurvivesDownstreamFailure() {
    var endpoint =
        endpoints.save(
            new WebhookEndpoint(
                UUID.randomUUID(), "failure", "abcdefghijklmnopqrstuvwx", Instant.now()));
    var destination =
        destinations.save(
            new WebhookDestination(
                UUID.randomUUID(), endpoint.getId(), "http://failure.invalid", Instant.now()));
    when(client.send(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(destination.getUrl()),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              var delivery = invocation.getArgument(2, WebhookDelivery.class);
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              try (var connection = dataSource.getConnection();
                  var query =
                      connection.prepareStatement(
                          "select status, completed_at from webhook_deliveries where id = ?")) {
                query.setObject(1, delivery.getId());
                try (var result = query.executeQuery()) {
                  assertThat(result.next()).isTrue();
                  assertThat(result.getString(1)).isEqualTo("PENDING");
                  assertThat(result.getObject(2)).isNull();
                }
              }
              return OutboundDeliveryClient.Result.http(500);
            });
    var response =
        http.postForEntity("/hooks/" + endpoint.getPublicKey(), "failure-body", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var eventId =
        jdbc.queryForObject(
            "select id from webhook_events where endpoint_id = ? order by received_at desc limit 1",
            UUID.class,
            endpoint.getId());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from webhook_deliveries where event_id = ? and status = 'FAILED'",
                Integer.class,
                eventId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from webhook_delivery_attempts where delivery_id in (select id from webhook_deliveries where event_id = ?) and attempt_number = 1 and outcome = 'FAILED' and http_status = 500 and error_code is null",
                Integer.class,
                eventId))
        .isEqualTo(1);
  }

  @Test
  void inboundRequestWaitsForSynchronousDelivery() throws Exception {
    var endpoint =
        endpoints.save(
            new WebhookEndpoint(
                UUID.randomUUID(), "sync", "abcdefghijklmnopqrstuvwx", Instant.now()));
    var destination =
        destinations.save(
            new WebhookDestination(
                UUID.randomUUID(), endpoint.getId(), "http://sync.invalid", Instant.now()));
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(client.send(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(destination.getUrl()),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              started.countDown();
              release.await(5, TimeUnit.SECONDS);
              return OutboundDeliveryClient.Result.http(204);
            });
    var request =
        CompletableFuture.supplyAsync(
            () -> http.postForEntity("/hooks/" + endpoint.getPublicKey(), "sync", String.class));
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(request.isDone()).isFalse();
    release.countDown();
    var response = request.get(5, TimeUnit.SECONDS);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var eventId =
        jdbc.queryForObject(
            "select id from webhook_events where endpoint_id = ? order by received_at desc limit 1",
            UUID.class,
            endpoint.getId());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from webhook_delivery_attempts where delivery_id in (select id from webhook_deliveries where event_id = ?)",
                Integer.class,
                eventId))
        .isEqualTo(1);
  }

  @Test
  void transportFailureProducesExactlyOneAttempt() {
    var endpoint =
        endpoints.save(
            new WebhookEndpoint(
                UUID.randomUUID(), "transport", "abcdefghijklmnopqrstuvwx", Instant.now()));
    var destination =
        destinations.save(
            new WebhookDestination(
                UUID.randomUUID(), endpoint.getId(), "http://transport.invalid", Instant.now()));
    when(client.send(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(destination.getUrl()),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(OutboundDeliveryClient.Result.failure("CONNECT_ERROR"));
    var response =
        http.postForEntity("/hooks/" + endpoint.getPublicKey(), "transport", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(client, times(1))
        .send(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(destination.getUrl()),
            org.mockito.ArgumentMatchers.any());
    var eventId =
        jdbc.queryForObject(
            "select id from webhook_events where endpoint_id = ? order by received_at desc limit 1",
            UUID.class,
            endpoint.getId());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from webhook_deliveries where event_id = ?",
                Integer.class,
                eventId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from webhook_delivery_attempts where delivery_id in (select id from webhook_deliveries where event_id = ?)",
                Integer.class,
                eventId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from webhook_delivery_attempts where attempt_number > 1",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select error_code from webhook_delivery_attempts where delivery_id in (select id from webhook_deliveries where event_id = ?)",
                String.class,
                eventId))
        .isEqualTo("CONNECT_ERROR");
  }
}
