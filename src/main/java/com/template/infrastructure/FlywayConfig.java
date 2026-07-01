package com.template.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
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
}
