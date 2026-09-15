package io.hookscope.endpoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_destinations")
public class WebhookDestination {
  @Id private UUID id;

  @Column(name = "endpoint_id", nullable = false)
  private UUID endpointId;

  @Column(nullable = false, columnDefinition = "text")
  private String url;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected WebhookDestination() {}

  public WebhookDestination(UUID id, UUID endpointId, String url, Instant createdAt) {
    this.id = id;
    this.endpointId = endpointId;
    this.url = url;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEndpointId() {
    return endpointId;
  }

  public String getUrl() {
    return url;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
