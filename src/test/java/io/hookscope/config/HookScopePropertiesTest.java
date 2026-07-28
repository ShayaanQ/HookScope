package io.hookscope.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HookScopePropertiesTest {

  @Test
  void usesTheLockedOneMiBDefaultForFutureIngestion() {
    HookScopeProperties properties = new HookScopeProperties();

    assertThat(properties.getIngestion().getMaximumBodySize()).isEqualTo(1_048_576);
  }
}
