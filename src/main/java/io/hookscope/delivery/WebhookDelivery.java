package io.hookscope.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {
  @Id private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "destination_id", nullable = false)
  private UUID destinationId;

  @Column(nullable = false, length = 16)
  private String kind;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected WebhookDelivery() {}

  public WebhookDelivery(
      UUID id, UUID eventId, UUID destinationId, String kind, String status, Instant createdAt) {
    this.id = id;
    this.eventId = eventId;
    this.destinationId = destinationId;
    this.kind = kind;
    this.status = status;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getDestinationId() {
    return destinationId;
  }

  public String getKind() {
    return kind;
  }

  public String getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void complete(String status, Instant completedAt) {
    this.status = status;
    this.completedAt = completedAt;
  }
}
