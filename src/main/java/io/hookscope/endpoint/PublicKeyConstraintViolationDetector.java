package io.hookscope.endpoint;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Identifies only the named database constraint that can represent a generated-key collision. */
@Component
public class PublicKeyConstraintViolationDetector {

  static final String PUBLIC_KEY_UNIQUE_CONSTRAINT = "webhook_endpoints_public_key_key";

  public boolean isPublicKeyUniqueViolation(DataIntegrityViolationException exception) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof ConstraintViolationException constraintViolation
          && PUBLIC_KEY_UNIQUE_CONSTRAINT.equals(constraintViolation.getConstraintName())) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
