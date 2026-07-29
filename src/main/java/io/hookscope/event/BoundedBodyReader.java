package io.hookscope.event;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.stereotype.Component;

@Component
public class BoundedBodyReader {
  public byte[] read(InputStream input, long maximumBodySize) throws IOException {
    if (maximumBodySize > Integer.MAX_VALUE - 1) {
      throw new IllegalArgumentException("Body limit is too large.");
    }
    int limit = (int) maximumBodySize;
    ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
    byte[] buffer = new byte[Math.min(8192, limit + 1)];
    int total = 0;
    while (total <= limit) {
      int permitted = Math.min(buffer.length, limit + 1 - total);
      int read = input.read(buffer, 0, permitted);
      if (read == -1) {
        return output.toByteArray();
      }
      output.write(buffer, 0, read);
      total += read;
    }
    throw new PayloadTooLargeException();
  }
}
