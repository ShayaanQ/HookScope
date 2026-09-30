package io.hookscope.delivery;

import io.hookscope.endpoint.WebhookDestinationRepository;
import io.hookscope.event.WebhookEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeliveryRetryWorker {
  private static final String CONSUMER = "hookscope-retry-worker";

  private final StringRedisTemplate redis;
  private final DeliveryRetryQueue queue;
  private final WebhookDeliveryRepository deliveries;
  private final WebhookEventRepository events;
  private final WebhookDestinationRepository destinations;
  private final DeliveryPersistenceService persistence;
  private final OutboundDeliveryClient client;

  public DeliveryRetryWorker(
      StringRedisTemplate r,
      DeliveryRetryQueue q,
      WebhookDeliveryRepository d,
      WebhookEventRepository e,
      WebhookDestinationRepository destinationRepository,
      DeliveryPersistenceService p,
      OutboundDeliveryClient c) {
    redis = r;
    queue = q;
    deliveries = d;
    events = e;
    destinations = destinationRepository;
    persistence = p;
    client = c;
  }

  @Scheduled(fixedDelayString = "${hookscope.delivery.retry-poll-interval-ms:500}")
  public void poll() {
    try {
      processOne();
    } catch (RuntimeException ignored) {
      // Redis may be unavailable during application shutdown or local development.
    }
  }

  public boolean processOne() {
    queue.ensureConsumerGroup();
    List<MapRecord<String, Object, Object>> records =
        redis
            .opsForStream()
            .read(
                Consumer.from(DeliveryRetryQueue.CONSUMER_GROUP, CONSUMER),
                StreamReadOptions.empty().count(1).block(Duration.ofMillis(50)),
                StreamOffset.create(DeliveryRetryQueue.RETRY_STREAM, ReadOffset.lastConsumed()));
    if (records == null || records.isEmpty()) {
      return false;
    }
    process(records.getFirst());
    return true;
  }

  private void process(MapRecord<String, Object, Object> record) {
    UUID deliveryId = UUID.fromString(String.valueOf(record.getValue().get("deliveryId")));
    int attemptNumber =
        Integer.parseInt(String.valueOf(record.getValue().get("nextAttemptNumber")));
    if (attemptNumber < 2 || attemptNumber > 3) {
      acknowledge(record);
      return;
    }
    var delivery = deliveries.findById(deliveryId).orElseThrow();
    var event = events.findById(delivery.getEventId()).orElseThrow();
    var destination = destinations.findById(delivery.getDestinationId()).orElseThrow();
    Instant started = Instant.now();
    var result = client.send(event, destination.getUrl(), delivery);
    persistence.complete(
        delivery,
        result,
        attemptNumber,
        started,
        Duration.between(started, Instant.now()).toMillis());
    if (!result.succeeded() && attemptNumber == 2) {
      queue.enqueue(deliveryId, 3);
    }
    if (!result.succeeded() && attemptNumber == 3) {
      queue.deadLetter(deliveryId, 3, result);
    }
    acknowledge(record);
  }

  private void acknowledge(MapRecord<String, Object, Object> record) {
    redis
        .opsForStream()
        .acknowledge(
            DeliveryRetryQueue.RETRY_STREAM, DeliveryRetryQueue.CONSUMER_GROUP, record.getId());
    redis.opsForStream().delete(DeliveryRetryQueue.RETRY_STREAM, record.getId());
  }
}
