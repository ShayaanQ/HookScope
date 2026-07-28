package io.hookscope.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuration for HookScope's temporary single-operator protection and ingestion limits. */
@Validated
@ConfigurationProperties(prefix = "hookscope")
public class HookScopeProperties {

  private String adminToken;

  @Valid private final Ingestion ingestion = new Ingestion();

  public String getAdminToken() {
    return adminToken;
  }

  public void setAdminToken(String adminToken) {
    this.adminToken = adminToken;
  }

  /**
   * The M1 token is deliberately not a user authentication system. It is temporary protection for
   * one operator until real authentication is introduced in a later milestone.
   */
  @PostConstruct
  void validateAdminToken() {
    if (adminToken == null || adminToken.isBlank() || adminToken.length() < 32) {
      throw new IllegalStateException(
          "HookScope admin token configuration is required and must be at least 32 characters.");
    }
  }

  public Ingestion getIngestion() {
    return ingestion;
  }

  public static class Ingestion {

    @Min(1)
    private long maximumBodySize = 1_048_576;

    public long getMaximumBodySize() {
      return maximumBodySize;
    }

    public void setMaximumBodySize(long maximumBodySize) {
      this.maximumBodySize = maximumBodySize;
    }
  }
}
