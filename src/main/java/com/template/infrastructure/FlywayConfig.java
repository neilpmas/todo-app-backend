package com.template.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot's DataSourceAutoConfiguration backs off whenever an R2DBC
 * ConnectionFactory bean is present, since it assumes a reactive-only app.
 * Flyway has no R2DBC driver, so a JDBC DataSource must be wired up explicitly
 * for migrations to run alongside the app's R2DBC repositories.
 */
@Configuration
public class FlywayConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties flywayDataSourceProperties() {
        return new DataSourceProperties();
    }

    @FlywayDataSource
    @Bean
    public javax.sql.DataSource flywayDataSource(DataSourceProperties flywayDataSourceProperties) {
        return flywayDataSourceProperties.initializeDataSourceBuilder().build();
    }

    /**
     * Takes the work away from the autoconfigured {@code FlywayMigrationInitializer},
     * which is an {@code InitializingBean} and would therefore migrate during
     * {@code ApplicationContext.refresh()} -- i.e. before the reactive web server binds
     * port 8080. {@link DatabaseMigrationRunner} runs {@code flyway.migrate()} instead,
     * as an {@code ApplicationRunner}, once the port is open.
     *
     * <p>Keeping the autoconfiguration (rather than {@code spring.flyway.enabled=false}
     * plus a hand-built Flyway) preserves all the {@code spring.flyway.*} binding --
     * schemas, locations, and the {@code connect-retries} that ride out Neon's cold-start
     * wake. The initializer bean still gets created; it just no longer opens a JDBC
     * connection, so nothing in the bean graph blocks on the database.
     *
     * <p>Do not remove this without also removing {@link DatabaseMigrationRunner}: with
     * neither, migrations would never run at all.
     */
    @Bean
    public FlywayMigrationStrategy deferMigrationsUntilAfterWebServerBind() {
        return _ -> {
            // Intentionally empty -- see DatabaseMigrationRunner.
        };
    }
}
