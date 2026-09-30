package io.hookscope.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_delivery_attempts")
public class WebhookDeliveryAttempt {
  @Id private UUID id;

  @Column(name = "delivery_id", nullable = false)
  private UUID deliveryId;

  @Column(name = "attempt_number", nullable = false)
  private int attemptNumber;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at", nullable = false)
  private Instant completedAt;

  @Column(nullable = false, length = 16)
  private String outcome;

  @Column(name = "http_status")
  private Integer httpStatus;

  @Column(name = "error_code", length = 32)
  private String errorCode;

  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  protected WebhookDeliveryAttempt() {}

  public WebhookDeliveryAttempt(
      UUID id,
      UUID deliveryId,
      int n,
      Instant s,
      Instant c,
      String o,
      Integer h,
      String e,
      long d) {
    this.id = id;
    this.deliveryId = deliveryId;
    this.attemptNumber = n;
    this.startedAt = s;
    this.completedAt = c;
    this.outcome = o;
    this.httpStatus = h;
    this.errorCode = e;
    this.durationMs = d;
  }

  public UUID getId() {
    return id;
  }

  public UUID getDeliveryId() {
    return deliveryId;
  }

  public int getAttemptNumber() {
    return attemptNumber;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public String getOutcome() {
    return outcome;
  }

  public Integer getHttpStatus() {
    return httpStatus;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public long getDurationMs() {
    return durationMs;
  }
}
