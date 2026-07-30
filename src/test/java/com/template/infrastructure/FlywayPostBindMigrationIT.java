package com.template.infrastructure;

import com.template.AbstractIntegrationTest;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the point of moving Flyway to an {@link org.springframework.boot.ApplicationRunner}:
 * that the reactive web server is already accepting TCP connections by the time Flyway
 * opens its first JDBC connection, and that readiness stays {@code REFUSING_TRAFFIC}
 * until migrations are done.
 *
 * <p>Unlike the other ITs this one leaves {@code spring.flyway.enabled} on, so the real
 * autoconfigured Flyway + {@link DatabaseMigrationRunner} path is exercised end to end,
 * against its own database in the shared Testcontainers Postgres.
 *
 * <p>{@code @SpringBootTest} calls {@code SpringApplication.run(...)} in full, runners
 * included, before any test method runs -- so everything asserted here is already
 * deterministic by the time the test body executes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlywayPostBindMigrationIT {

    private static final String DATABASE = "post_bind_it";

    static {
        // Reuse the container AbstractIntegrationTest already starts (referencing the field
        // triggers its static start), but on a database of our own: the other ITs migrate
        // theirs by hand in @BeforeAll, and V1 hardcodes the "app" schema, so this test
        // needs somewhere untouched for the real autoconfigured Flyway to migrate into.
        PostgreSQLContainer<?> postgres = AbstractIntegrationTest.POSTGRES;
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("drop database if exists " + DATABASE);
            statement.execute("create database " + DATABASE);
        }
        catch (SQLException ex) {
            throw new IllegalStateException("could not create the " + DATABASE + " database", ex);
        }
    }

    private static String hostAndPort() {
        PostgreSQLContainer<?> postgres = AbstractIntegrationTest.POSTGRES;
        return postgres.getHost() + ":" + postgres.getMappedPort(5432);
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> postgres = AbstractIntegrationTest.POSTGRES;
        // The one IT that leaves Flyway autoconfiguration switched on.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + hostAndPort() + "/" + DATABASE);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + hostAndPort() + "/" + DATABASE);
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    /**
     * A Flyway {@link Callback} is the one hook that fires inside {@code flyway.migrate()},
     * so it is the only place that can observe the world as it was mid-migration.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class BeforeMigrateProbeConfig {
        @Bean
        BeforeMigrateProbe beforeMigrateProbe(ApplicationContext applicationContext) {
            return new BeforeMigrateProbe(applicationContext);
        }
    }

    static class BeforeMigrateProbe implements Callback {

        private final ApplicationContext applicationContext;

        volatile boolean fired;
        volatile boolean webServerListening;

        // Genuinely null until handle() fires -- there is no sensible default
        // ReadinessState to pre-fill, and doing so would mask handle() never firing at
        // all rather than surfacing it as a clear assertion failure.
        volatile @Nullable ReadinessState readinessDuringMigration;

        BeforeMigrateProbe(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }

        @Override
        public boolean supports(Event event, Context context) {
            return event == Event.BEFORE_MIGRATE;
        }

        @Override
        public boolean canHandleInTransaction(Event event, Context context) {
            return true;
        }

        @Override
        public void handle(Event event, Context context) {
            this.fired = true;
            this.readinessDuringMigration = this.applicationContext.getBean(ApplicationAvailability.class)
                .getReadinessState();
            // getWebServer() can legitimately be null per its contract, but not here: this
            // callback firing at all means the runner is already past web server startup
            // (see DatabaseMigrationRunner's javadoc for why). A null here would mean the
            // very invariant this test exists to prove has already been violated.
            WebServer webServer = Objects.requireNonNull(
                ((WebServerApplicationContext) this.applicationContext).getWebServer(),
                "web server must already be started by the time a migration callback fires");
            int port = webServer.getPort();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
                this.webServerListening = true;
            }
            catch (Exception ex) {
                this.webServerListening = false;
            }
        }

        @Override
        public String getCallbackName() {
            return "beforeMigrateProbe";
        }
    }

    @Autowired
    private BeforeMigrateProbe probe;

    @Autowired
    private DatabaseMigrationRunner migrationRunner;

    @Autowired
    private ApplicationAvailability availability;

    @Autowired
    private DataSource dataSource;

    @Test
    void webServerIsListeningBeforeFlywayRuns() {
        assertThat(this.probe.fired)
            .as("Flyway must actually have run via DatabaseMigrationRunner")
            .isTrue();
        assertThat(this.probe.webServerListening)
            .as("port must already be bound when Flyway starts migrating")
            .isTrue();
    }

    @Test
    void readinessRefusesTrafficUntilMigrationsFinish() {
        assertThat(this.probe.readinessDuringMigration)
            .as("ApplicationReadyEvent (which publishes ACCEPTING_TRAFFIC) fires after runners")
            .isEqualTo(ReadinessState.REFUSING_TRAFFIC);
        assertThat(this.availability.getReadinessState())
            .isEqualTo(ReadinessState.ACCEPTING_TRAFFIC);
    }

    @Test
    void migrationsActuallyApplied() throws Exception {
        assertThat(this.migrationRunner.isComplete()).isTrue();
        try (Connection connection = this.dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                 "select count(*) from information_schema.tables"
                     + " where table_schema = 'app' and table_name = 'todos'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }
}
