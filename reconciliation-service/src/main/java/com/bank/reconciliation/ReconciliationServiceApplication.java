package com.bank.reconciliation;

import com.bank.reconciliation.config.JpaBuilderConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Reconciliation Service — "the internal auditor".
 *
 * Read-only against Oracle (account_master / transaction_master),
 * read-write against Postgres (recon_run_audit / recon_result_audit /
 * ledger_mutation_audit / outbox_audit). Both datasources are wired
 * explicitly in the config package, so the default Spring Boot
 * DataSource/JPA autoconfiguration is disabled here on purpose.
 *
 * Excluding HibernateJpaAutoConfiguration also drops the
 * EntityManagerFactoryBuilder bean it would normally provide, which
 * OracleConfig/PostgresConfig both need - JpaBuilderConfig re-supplies
 * it. Imported explicitly (not left to component scanning alone) so
 * it's unconditionally on the classpath and registered.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@Import(JpaBuilderConfig.class)
@EnableScheduling
public class ReconciliationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconciliationServiceApplication.class, args);
    }
}

