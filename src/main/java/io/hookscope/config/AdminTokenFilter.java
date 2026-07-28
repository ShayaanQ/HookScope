package io.hookscope.config;

import io.hookscope.api.error.ProblemDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** Applies the temporary single-operator token only to the management API. */
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

  static final String ADMIN_TOKEN_HEADER = "X-HookScope-Admin-Token";
  private static final PathPattern MANAGEMENT_API_PATTERN =
      PathPatternParser.defaultInstance.parse("/api/v1/**");

  private final AdminTokenVerifier verifier;

  public AdminTokenFilter(AdminTokenVerifier verifier) {
    this.verifier = verifier;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    ServletRequestPathUtils.parseAndCache(request);
    return !MANAGEMENT_API_PATTERN.matches(
        ServletRequestPathUtils.getParsedRequestPath(request).pathWithinApplication());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!verifier.matches(request.getHeader(ADMIN_TOKEN_HEADER))) {
      ProblemDetails.write(
          response,
          request,
          HttpStatus.UNAUTHORIZED,
          "UNAUTHORIZED",
          "Unauthorized",
          "A valid administrator token is required.");
      return;
    }
    filterChain.doFilter(request, response);
  }
}
