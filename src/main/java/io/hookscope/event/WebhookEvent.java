package io.hookscope.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "webhook_events")
public class WebhookEvent {
  @Id private UUID id;

  @Column(name = "endpoint_id", nullable = false)
  private UUID endpointId;

  @Column(nullable = false, length = 10)
  private String method;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, List<String>> headers;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "query_parameters", nullable = false, columnDefinition = "jsonb")
  private Map<String, List<String>> queryParameters;

  @Column(name = "content_type", length = 255)
  private String contentType;

  @Column(nullable = false)
  private byte[] body;

  @Column(name = "body_size", nullable = false)
  private long bodySize;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "body_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
  private String bodySha256;

  @ColumnTransformer(write = "?::inet")
  @Column(name = "source_ip", nullable = false, columnDefinition = "inet")
  private String sourceIp;

  @Column(nullable = false, length = 1024)
  private String path;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;

  protected WebhookEvent() {}

  public WebhookEvent(
      UUID id,
      UUID endpointId,
      String method,
      Map<String, List<String>> headers,
      Map<String, List<String>> queryParameters,
      String contentType,
      byte[] body,
      long bodySize,
      String bodySha256,
      String sourceIp,
      String path,
      Instant receivedAt) {
    this.id = id;
    this.endpointId = endpointId;
    this.method = method;
    this.headers = headers;
    this.queryParameters = queryParameters;
    this.contentType = contentType;
    this.body = body;
    this.bodySize = bodySize;
    this.bodySha256 = bodySha256;
    this.sourceIp = sourceIp;
    this.path = path;
    this.receivedAt = receivedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEndpointId() {
    return endpointId;
  }

  public String getMethod() {
    return method;
  }

  public Map<String, List<String>> getHeaders() {
    return headers;
  }

  public Map<String, List<String>> getQueryParameters() {
    return queryParameters;
  }

  public String getContentType() {
    return contentType;
  }

  public byte[] getBody() {
    return body;
  }

  public long getBodySize() {
    return bodySize;
  }

  public String getBodySha256() {
    return bodySha256;
  }

  public String getSourceIp() {
    return sourceIp;
  }

  public String getPath() {
    return path;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }
}
