package io.hookscope.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

/** Verifies the temporary M1 operator token without a timing-sensitive string comparison. */
@Component
public class AdminTokenVerifier {

  private final byte[] configuredToken;

  public AdminTokenVerifier(HookScopeProperties properties) {
    configuredToken = properties.getAdminToken().getBytes(StandardCharsets.UTF_8);
  }

  public boolean matches(String suppliedToken) {
    if (suppliedToken == null) {
      return false;
    }
    return MessageDigest.isEqual(configuredToken, suppliedToken.getBytes(StandardCharsets.UTF_8));
  }
}
