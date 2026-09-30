package io.hookscope;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DeliverySchemaIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10");

  @Autowired JdbcTemplate jdbc;

  @Test
  void exposesLockedDeliveryTablesAndConstraints() {
    assertThat(
            jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema='public' and table_name in ('webhook_delivery_attempts','webhook_deliveries') order by table_name",
                String.class))
        .containsExactly("webhook_deliveries", "webhook_delivery_attempts");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from pg_constraint where conname='webhook_delivery_attempts_delivery_number_key'",
                Integer.class))
        .isEqualTo(1);
    List<String> indexes =
        jdbc.queryForList(
            "select indexdef from pg_indexes where schemaname='public' and tablename='webhook_deliveries'",
            String.class);
    assertThat(indexes)
        .anyMatch(
            value ->
                value.contains("event_id")
                    && value.contains("created_at DESC")
                    && value.contains("id DESC"));
  }
}
