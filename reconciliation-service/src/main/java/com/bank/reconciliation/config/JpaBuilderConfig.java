package com.bank.reconciliation.config;

import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.util.HashMap;

/**
 * ReconciliationServiceApplication excludes HibernateJpaAutoConfiguration
 * because this service builds two separate EntityManagerFactory/DataSource
 * pairs by hand (see OracleConfig, PostgresConfig) instead of relying on
 * Boot's single-datasource default wiring. That exclusion also removes the
 * EntityManagerFactoryBuilder bean HibernateJpaAutoConfiguration would
 * normally register - and both OracleConfig.oracleEntityManagerFactory(...)
 * and PostgresConfig.postgresEntityManagerFactory(...) take one as a
 * constructor/method parameter. This is the one place that bean is
 * re-provided so both configs can be autowired again.
 */
@Configuration
public class JpaBuilderConfig {

    @Bean
    public JpaVendorAdapter jpaVendorAdapter() {
        return new HibernateJpaVendorAdapter();
    }

    @Bean
    public EntityManagerFactoryBuilder entityManagerFactoryBuilder(JpaVendorAdapter jpaVendorAdapter) {
        // No shared JPA properties or PersistenceUnitManager here - both
        // OracleConfig and PostgresConfig pass their own per-datasource
        // properties map (dialect, schema, ddl-auto) directly to .properties(...)
        // when they call this builder, so nothing needs to be pre-seeded.
        return new EntityManagerFactoryBuilder(jpaVendorAdapter, new HashMap<>(), null);
    }
}
