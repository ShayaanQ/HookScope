package io.hookscope.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminTokenVerifierTest {

  @Test
  void acceptsOnlyTheConfiguredToken() {
    HookScopeProperties properties = new HookScopeProperties();
    properties.setAdminToken("isolated-unit-test-token-not-for-production");
    AdminTokenVerifier verifier = new AdminTokenVerifier(properties);

    assertThat(verifier.matches("isolated-unit-test-token-not-for-production")).isTrue();
    assertThat(verifier.matches("isolated-unit-test-token-not-for-productiom")).isFalse();
    assertThat(verifier.matches(null)).isFalse();
  }
}
