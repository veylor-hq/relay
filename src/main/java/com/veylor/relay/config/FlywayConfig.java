package com.veylor.relay.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    private final DataSource dataSource;

    @org.springframework.beans.factory.annotation.Value("${spring.flyway.enabled:true}")
    private boolean flywayEnabled;

    @org.springframework.beans.factory.annotation.Value("${spring.flyway.baseline-on-migrate:false}")
    private boolean baselineOnMigrate;

    public FlywayConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void migrate() {
        if (!flywayEnabled) {
            return;
        }
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(baselineOnMigrate)
                .load();

        flyway.repair();
        flyway.migrate();
    }
}
