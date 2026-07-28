package io.hookscope.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Foundation configuration. Admin-token enforcement begins in M1-B. */
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
