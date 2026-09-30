package io.hookscope.delivery;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeliveryRetryQueue {
  public static final String RETRY_STREAM = "hookscope:delivery-retries";
  public static final String CONSUMER_GROUP = "hookscope-retry-workers";
  public static final String DLQ_STREAM = "hookscope:delivery-dlq";

  private final StringRedisTemplate redis;

  public DeliveryRetryQueue(StringRedisTemplate r) {
    redis = r;
  }

  public void enqueue(UUID deliveryId, int nextAttemptNumber) {
    ensureConsumerGroup();
    redis
        .opsForStream()
        .add(
            RETRY_STREAM,
            Map.of(
                "deliveryId",
                deliveryId.toString(),
                "nextAttemptNumber",
                String.valueOf(nextAttemptNumber)));
  }

  public void deadLetter(UUID deliveryId, int attempts, OutboundDeliveryClient.Result result) {
    String finalResult =
        result.status() == null ? result.errorCode() : String.valueOf(result.status());
    redis
        .opsForStream()
        .add(
            DLQ_STREAM,
            Map.of(
                "deliveryId", deliveryId.toString(),
                "attempts", String.valueOf(attempts),
                "finalResult", finalResult,
                "timestamp", Instant.now().toString()));
  }

  public void ensureConsumerGroup() {
    try {
      RedisCallback<Object> createGroup =
          connection ->
              connection.execute(
                  "XGROUP",
                  "CREATE".getBytes(StandardCharsets.UTF_8),
                  RETRY_STREAM.getBytes(StandardCharsets.UTF_8),
                  CONSUMER_GROUP.getBytes(StandardCharsets.UTF_8),
                  "$".getBytes(StandardCharsets.UTF_8),
                  "MKSTREAM".getBytes(StandardCharsets.UTF_8));
      redis.execute(createGroup);
    } catch (DataAccessException ignored) {
      // BUSYGROUP means the one required consumer group already exists.
    }
  }
}
