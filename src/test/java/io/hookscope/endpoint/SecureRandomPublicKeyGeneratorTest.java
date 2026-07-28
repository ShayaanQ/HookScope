package io.hookscope.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SecureRandomPublicKeyGeneratorTest {

  private static final Pattern PUBLIC_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{32}");

  @Test
  void generatesTenThousandUniqueUrlSafeKeysWithTwentyFourDecodedBytes() {
    PublicKeyGenerator generator = new SecureRandomPublicKeyGenerator();
    Set<String> keys = new HashSet<>();

    for (int index = 0; index < 10_000; index++) {
      String key = generator.generate();
      assertThat(PUBLIC_KEY_PATTERN.matcher(key).matches()).isTrue();
      assertThat(Base64.getUrlDecoder().decode(key)).hasSize(24);
      keys.add(key);
    }

    assertThat(keys).hasSize(10_000);
  }
}
