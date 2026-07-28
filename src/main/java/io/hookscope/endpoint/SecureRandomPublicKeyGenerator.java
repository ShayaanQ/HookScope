package io.hookscope.endpoint;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** Generates independent 192-bit public endpoint keys. */
@Component
public class SecureRandomPublicKeyGenerator implements PublicKeyGenerator {

  private static final int RANDOM_BYTE_COUNT = 24;

  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  public String generate() {
    byte[] randomBytes = new byte[RANDOM_BYTE_COUNT];
    secureRandom.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }
}
