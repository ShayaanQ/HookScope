package io.hookscope;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Release-level PostgreSQL evidence for the complete M1 schema and list-query contracts. */
@Testcontainers
class ReleaseVerificationIntegrationTest {

  private static final String RELEASE_SCHEMA = "public";
  private static final int REPRESENTATIVE_ENDPOINT_COUNT = 400;
  private static final int REPRESENTATIVE_EVENT_COUNT = 2_500;
  private static final int SELECTED_ENDPOINT_EVENT_COUNT = 600;

  @Container PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.10");

  private DataSource dataSource;
  private JdbcTemplate jdbc;

  @BeforeEach
  void connectToTheUntouchedDatabase() {
    dataSource =
        new SimpleDriverDataSource(
            new org.postgresql.Driver(),
            postgres.getJdbcUrl(),
            postgres.getUsername(),
            postgres.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' "
                    + "AND table_name IN ('flyway_schema_history', 'webhook_endpoints', "
                    + "'webhook_events')",
                Integer.class))
        .isZero();
  }

  @Test
  void migratesAnEmptyDatabaseInOrderAndProducesTheLockedFinalSchema() {
    Flyway flyway = migrateEmptyReleaseSchema();

    assertThat(
            jdbc.queryForList(
                "SELECT installed_rank || ':' || version || ':' || success "
                    + "FROM "
                    + RELEASE_SCHEMA
                    + ".flyway_schema_history WHERE installed_rank > 0 ORDER BY installed_rank",
                String.class))
        .containsExactly("1:1:true", "2:2:true", "3:3:true");
    flyway.validate();
    assertThat(
            java.util.Arrays.stream(flyway.info().applied())
                .filter(migration -> migration.getVersion() != null)
                .map(migration -> migration.getVersion().getVersion()))
        .containsExactly("1", "2", "3");
    assertThat(
            jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ? "
                    + "ORDER BY table_name",
                String.class,
                RELEASE_SCHEMA))
        .containsExactly(
            "flyway_schema_history", "webhook_destinations", "webhook_endpoints", "webhook_events");
    assertColumns(
        "webhook_endpoints",
        List.of(
            "id:uuid:NO::",
            "name:character varying:NO:120:",
            "public_key:character varying:NO:32:",
            "created_at:timestamp with time zone:NO::default"));
    assertColumns(
        "webhook_events",
        List.of(
            "id:uuid:NO::",
            "endpoint_id:uuid:NO::",
            "method:character varying:NO:10:",
            "headers:jsonb:NO::",
            "query_parameters:jsonb:NO::",
            "content_type:character varying:YES:255:",
            "body:bytea:NO::",
            "body_size:bigint:NO::",
            "body_sha256:character:NO:64:",
            "source_ip:inet:NO::",
            "path:character varying:NO:1024:",
            "received_at:timestamp with time zone:NO::default"));
    assertThat(columnDefault("webhook_endpoints", "created_at"))
        .containsIgnoringCase("current_timestamp");
    assertThat(columnDefault("webhook_events", "received_at"))
        .containsIgnoringCase("current_timestamp");
    assertThat(constraintDefinitions())
        .contains(
            "webhook_endpoints_pkey:p:PRIMARY KEY (id)",
            "webhook_endpoints_public_key_key:u:UNIQUE (public_key)",
            "webhook_events_pkey:p:PRIMARY KEY (id)",
            "webhook_events_endpoint_id_fkey:f:FOREIGN KEY (endpoint_id) REFERENCES "
                + "webhook_endpoints(id)",
            "webhook_events_body_size_nonnegative:c:CHECK ((body_size >= 0))",
            "webhook_events_body_sha256_format:c:CHECK ((body_sha256 ~ '^[0-9a-f]{64}$'::text))");
    assertThat(
            jdbc.queryForObject(
                "SELECT confdeltype FROM pg_constraint WHERE conname = "
                    + "'webhook_events_endpoint_id_fkey' AND connamespace = ?::regnamespace",
                String.class,
                RELEASE_SCHEMA))
        .isEqualTo("a");
    assertThat(indexDefinitions())
        .containsExactlyInAnyOrder(
            "CREATE UNIQUE INDEX webhook_endpoints_pkey ON "
                + RELEASE_SCHEMA
                + ".webhook_endpoints USING btree (id)",
            "CREATE UNIQUE INDEX webhook_endpoints_public_key_key ON "
                + RELEASE_SCHEMA
                + ".webhook_endpoints USING btree (public_key)",
            "CREATE INDEX webhook_endpoints_created_at_id_desc_idx ON "
                + RELEASE_SCHEMA
                + ".webhook_endpoints USING btree (created_at DESC, id DESC)",
            "CREATE UNIQUE INDEX webhook_events_pkey ON "
                + RELEASE_SCHEMA
                + ".webhook_events USING btree (id)",
            "CREATE INDEX webhook_events_endpoint_id_received_at_id_desc_idx ON "
                + RELEASE_SCHEMA
                + ".webhook_events USING btree (endpoint_id, received_at DESC, id DESC)");
  }

  @Test
  void capturesRepresentativeUnforcedPlansForTheRequiredListQueries() {
    migrateEmptyReleaseSchema();
    List<UUID> endpointIds =
        java.util.stream.IntStream.range(0, REPRESENTATIVE_ENDPOINT_COUNT)
            .mapToObj(ignored -> UUID.randomUUID())
            .toList();
    UUID endpointId = endpointIds.getFirst();
    jdbc.batchUpdate(
        "INSERT INTO "
            + RELEASE_SCHEMA
            + ".webhook_endpoints (id, name, public_key, created_at) VALUES (?, ?, ?, ?)",
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(java.sql.PreparedStatement statement, int index)
              throws java.sql.SQLException {
            statement.setObject(1, endpointIds.get(index));
            statement.setString(2, "Release verification " + index);
            statement.setString(3, String.format("%032d", index));
            statement.setTimestamp(
                4,
                java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index)));
          }

          @Override
          public int getBatchSize() {
            return endpointIds.size();
          }
        });
    jdbc.batchUpdate(
        "INSERT INTO "
            + RELEASE_SCHEMA
            + ".webhook_events "
            + "(id, endpoint_id, method, headers, query_parameters, body, body_size, "
            + "body_sha256, source_ip, path, received_at) "
            + "VALUES (?, ?, 'POST', '{}'::jsonb, '{}'::jsonb, ?, 0, ?, '127.0.0.1'::inet, "
            + "'/hooks/release-verification', ?)",
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(java.sql.PreparedStatement statement, int index)
              throws java.sql.SQLException {
            int endpointIndex =
                index < SELECTED_ENDPOINT_EVENT_COUNT
                    ? 0
                    : 1 + (index % (REPRESENTATIVE_ENDPOINT_COUNT - 1));
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, endpointIds.get(endpointIndex));
            statement.setBytes(3, new byte[0]);
            statement.setString(4, "0".repeat(64));
            statement.setTimestamp(
                5,
                java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index)));
          }

          @Override
          public int getBatchSize() {
            return REPRESENTATIVE_EVENT_COUNT;
          }
        });
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM " + RELEASE_SCHEMA + ".webhook_endpoints", Long.class))
        .isEqualTo(REPRESENTATIVE_ENDPOINT_COUNT);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM " + RELEASE_SCHEMA + ".webhook_events", Long.class))
        .isEqualTo(REPRESENTATIVE_EVENT_COUNT);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM " + RELEASE_SCHEMA + ".webhook_events WHERE endpoint_id = ?",
                Long.class,
                endpointId))
        .isEqualTo(SELECTED_ENDPOINT_EVENT_COUNT);
    jdbc.execute("ANALYZE " + RELEASE_SCHEMA + ".webhook_endpoints");
    jdbc.execute("ANALYZE " + RELEASE_SCHEMA + ".webhook_events");

    List<String> endpointPlan =
        jdbc.queryForList(
            "EXPLAIN (COSTS OFF) SELECT id, name, public_key, created_at FROM "
                + RELEASE_SCHEMA
                + ".webhook_endpoints ORDER BY created_at DESC, id DESC LIMIT 20",
            String.class);
    List<String> eventPlan =
        jdbc.queryForList(
            "EXPLAIN (COSTS OFF) SELECT id, method, path, content_type, body_size, source_ip, "
                + "received_at FROM "
                + RELEASE_SCHEMA
                + ".webhook_events WHERE endpoint_id = ? "
                + "ORDER BY received_at DESC, id DESC LIMIT 20 OFFSET 0",
            String.class,
            endpointId);

    assertThat(endpointPlan).isNotEmpty().allMatch(line -> !line.isBlank());
    assertThat(eventPlan).isNotEmpty().allMatch(line -> !line.isBlank());
  }

  private Flyway migrateEmptyReleaseSchema() {
    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .schemas(RELEASE_SCHEMA)
            .defaultSchema(RELEASE_SCHEMA)
            .createSchemas(false)
            .locations("classpath:db/migration")
            .load();
    MigrateResult result = flyway.migrate();
    assertThat(result.migrationsExecuted).isEqualTo(3);
    return flyway;
  }

  private void assertColumns(String table, List<String> expected) {
    assertThat(
            jdbc.queryForList(
                "SELECT column_name || ':' || data_type || ':' || is_nullable || ':' || "
                    + "COALESCE(character_maximum_length::text, '') || ':' || "
                    + "CASE WHEN column_default IS NULL THEN '' ELSE 'default' END "
                    + "FROM information_schema.columns WHERE table_schema = ? AND table_name = ? "
                    + "ORDER BY ordinal_position",
                String.class,
                RELEASE_SCHEMA,
                table))
        .containsExactlyElementsOf(expected);
  }

  private List<String> constraintDefinitions() {
    return jdbc.queryForList(
        "SELECT conname || ':' || contype::text || ':' || pg_get_constraintdef(oid) "
            + "FROM pg_constraint "
            + "WHERE connamespace = ?::regnamespace ORDER BY conname",
        String.class,
        RELEASE_SCHEMA);
  }

  private String columnDefault(String table, String column) {
    return jdbc.queryForObject(
        "SELECT column_default FROM information_schema.columns "
            + "WHERE table_schema = ? AND table_name = ? AND column_name = ?",
        String.class,
        RELEASE_SCHEMA,
        table,
        column);
  }

  private List<String> indexDefinitions() {
    return jdbc.queryForList(
        "SELECT indexdef FROM pg_indexes WHERE schemaname = ? "
            + "AND tablename IN ('webhook_endpoints', 'webhook_events') ORDER BY indexname",
        String.class,
        RELEASE_SCHEMA);
  }
}
