package io.hookscope.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class HookScopePropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
          .withUserConfiguration(PropertiesConfiguration.class);

  @Test
  void usesTheLockedOneMiBDefaultForFutureIngestion() {
    HookScopeProperties properties = new HookScopeProperties();

    assertThat(properties.getIngestion().getMaximumBodySize()).isEqualTo(1_048_576);
  }

  @Test
  void rejectsMissingBlankAndShortTemporaryAdminTokensWithASanitizedMessage() {
    assertInvalidToken(null);
    assertInvalidToken("   ");
    assertInvalidToken("too-short");
  }

  @Test
  void failsApplicationConfigurationForMissingBlankAndShortTokens() {
    assertContextFails();
    assertContextFails("hookscope.admin-token=   ");
    assertContextFails("hookscope.admin-token=too-short");
  }

  private void assertInvalidToken(String token) {
    HookScopeProperties properties = new HookScopeProperties();
    properties.setAdminToken(token);

    assertThatThrownBy(properties::validateAdminToken)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "HookScope admin token configuration is required and must be at least 32 characters.");
  }

  private void assertContextFails(String... properties) {
    contextRunner
        .withPropertyValues(properties)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "HookScope admin token configuration is required and must be at least 32 characters.");
              assertThat(context.getStartupFailure().getMessage()).doesNotContain("too-short");
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(HookScopeProperties.class)
  static class PropertiesConfiguration {}
}
