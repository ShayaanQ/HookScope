package io.hookscope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.hookscope.delivery.DeliveryRetryQueue;
import io.hookscope.delivery.DeliveryRetryWorker;
import io.hookscope.delivery.InitialDeliveryOrchestrator;
import io.hookscope.delivery.OutboundDeliveryClient;
import io.hookscope.delivery.WebhookDeliveryAttemptRepository;
import io.hookscope.delivery.WebhookDeliveryRepository;
import io.hookscope.endpoint.WebhookDestination;
import io.hookscope.endpoint.WebhookDestinationRepository;
import io.hookscope.endpoint.WebhookEndpoint;
import io.hookscope.endpoint.WebhookEndpointRepository;
import io.hookscope.event.WebhookEvent;
import io.hookscope.event.WebhookEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = "hookscope.delivery.retry-worker-enabled=false")
class DeliveryRetryIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
  }

  @Autowired private WebhookEndpointRepository endpoints;
  @Autowired private WebhookDestinationRepository destinations;
  @Autowired private WebhookEventRepository events;
  @Autowired private WebhookDeliveryRepository deliveries;
  @Autowired private WebhookDeliveryAttemptRepository attempts;
  @Autowired private InitialDeliveryOrchestrator initialDelivery;
  @Autowired private DeliveryRetryWorker worker;
  @Autowired private StringRedisTemplate redis;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private OutboundDeliveryClient client;

  @BeforeEach
  void clear() {
    redis.delete(List.of(DeliveryRetryQueue.RETRY_STREAM, DeliveryRetryQueue.DLQ_STREAM));
    jdbc.update("DELETE FROM webhook_delivery_attempts");
    jdbc.update("DELETE FROM webhook_deliveries");
    jdbc.update("DELETE FROM webhook_destinations");
    jdbc.update("DELETE FROM webhook_events");
    jdbc.update("DELETE FROM webhook_endpoints");
  }

  @Test
  void firstAttemptSuccessCreatesNoRetryJobAndOneAttempt() {
    var fixture = fixture();
    when(client.send(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(fixture.destination().getUrl()),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(OutboundDeliveryClient.Result.http(204));

    initialDelivery.deliver(fixture.event());

    assertThat(attempts.count()).isEqualTo(1);
    assertThat(deliveries.findAll())
        .singleElement()
        .extracting(d -> d.getStatus())
        .isEqualTo("SUCCEEDED");
    assertThat(redis.opsForStream().size(DeliveryRetryQueue.RETRY_STREAM)).isZero();
    assertThat(redis.hasKey(DeliveryRetryQueue.DLQ_STREAM)).isFalse();
  }

  @Test
  void failedFirstAttemptRetriesOnceAndSucceeds() {
    var fixture = fixture();
    when(client.send(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(fixture.destination().getUrl()),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            OutboundDeliveryClient.Result.http(500), OutboundDeliveryClient.Result.http(204));

    initialDelivery.deliver(fixture.event());
    assertThat(worker.processOne()).isTrue();

    var delivery = deliveries.findAll().getFirst();
    assertThat(attempts.findByDeliveryIdOrderByAttemptNumberAsc(delivery.getId()))
        .extracting(a -> a.getAttemptNumber())
        .containsExactly(1, 2);
    assertThat(delivery.getStatus()).isEqualTo("SUCCEEDED");
    assertThat(redis.opsForStream().size(DeliveryRetryQueue.RETRY_STREAM)).isZero();
    assertThat(redis.hasKey(DeliveryRetryQueue.DLQ_STREAM)).isFalse();
    assertThat(worker.processOne()).isFalse();
  }

  @Test
  void threeFailuresCreateOneDlqEntryAndNoFourthAttempt() {
    var fixture = fixture();
    when(client.send(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(fixture.destination().getUrl()),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            OutboundDeliveryClient.Result.failure("CONNECT_ERROR"),
            OutboundDeliveryClient.Result.failure("CONNECT_ERROR"),
            OutboundDeliveryClient.Result.failure("CONNECT_ERROR"));

    initialDelivery.deliver(fixture.event());
    assertThat(worker.processOne()).isTrue();
    assertThat(worker.processOne()).isTrue();

    var delivery = deliveries.findAll().getFirst();
    assertThat(attempts.findByDeliveryIdOrderByAttemptNumberAsc(delivery.getId()))
        .extracting(a -> a.getAttemptNumber())
        .containsExactly(1, 2, 3);
    assertThat(delivery.getStatus()).isEqualTo("FAILED");
    assertThat(redis.opsForStream().size(DeliveryRetryQueue.DLQ_STREAM)).isEqualTo(1);
    assertThat(redis.opsForStream().size(DeliveryRetryQueue.RETRY_STREAM)).isZero();
    assertThat(worker.processOne()).isFalse();
  }

  private Fixture fixture() {
    var endpoint =
        endpoints.save(
            new WebhookEndpoint(
                UUID.randomUUID(), "retry", "abcdefghijklmnopqrstuvwx", Instant.now()));
    var destination =
        destinations.save(
            new WebhookDestination(
                UUID.randomUUID(), endpoint.getId(), "http://retry.invalid", Instant.now()));
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
    return new Fixture(event, destination);
  }

  private record Fixture(WebhookEvent event, WebhookDestination destination) {}
}
