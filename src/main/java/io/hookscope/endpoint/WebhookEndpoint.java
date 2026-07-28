package io.hookscope.endpoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint {

  @Id private UUID id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "public_key", nullable = false, length = 32)
  private String publicKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected WebhookEndpoint() {}

  public WebhookEndpoint(UUID id, String name, String publicKey, Instant createdAt) {
    this.id = id;
    this.name = name;
    this.publicKey = publicKey;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getPublicKey() {
    return publicKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
