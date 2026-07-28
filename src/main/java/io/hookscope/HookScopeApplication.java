package io.hookscope;

import io.hookscope.config.HookScopeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(HookScopeProperties.class)
public class HookScopeApplication {

  public static void main(String[] args) {
    SpringApplication.run(HookScopeApplication.class, args);
  }
}
