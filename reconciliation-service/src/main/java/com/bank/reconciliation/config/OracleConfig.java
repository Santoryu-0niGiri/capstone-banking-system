package com.bank.reconciliation.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Read-only connection into the Oracle XE master DB. Recon only ever
 * SELECTs here (account_master, transaction_master) - it never writes
 * back into Oracle, matching the "read balances (read-only)" edge in
 * the architecture diagram.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.bank.reconciliation.repository.oracle",
        entityManagerFactoryRef = "oracleEntityManagerFactory",
        transactionManagerRef = "oracleTransactionManager"
)
public class OracleConfig {

    @Bean
    @ConfigurationProperties("spring.oracle.datasource")
    public DataSource oracleDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * @Qualifier is not optional here: postgresDataSource (PostgresConfig)
     * is marked @Primary, and Spring resolves @Primary BEFORE falling back
     * to matching a same-named parameter. Without this qualifier,
     * "DataSource oracleDataSource" silently receives the Postgres
     * DataSource despite the name matching the intended bean exactly -
     * which is exactly what caused every "oracle" persistence-unit query
     * to execute against Postgres and fail with
     * "relation ... does not exist".
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean oracleEntityManagerFactory(
            EntityManagerFactoryBuilder builder, @Qualifier("oracleDataSource") DataSource oracleDataSource) {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", "none");
        props.put("hibernate.default_schema", System.getenv().getOrDefault("ORACLE_SCHEMA", "LEDGER_APP"));
        props.put("hibernate.dialect", "org.hibernate.dialect.OracleDialect");
        return builder
                .dataSource(oracleDataSource)
                .packages("com.bank.reconciliation.entity.oracle")
                .persistenceUnit("oracle")
                .properties(props)
                .build();
    }

    /**
     * Same @Qualifier requirement as above: postgresEntityManagerFactory
     * is also marked @Primary, so an unqualified same-type parameter here
     * would silently wrap the Postgres EntityManagerFactory in this
     * "oracle" transaction manager instead of the Oracle one.
     */
    @Bean
    public PlatformTransactionManager oracleTransactionManager(
            @Qualifier("oracleEntityManagerFactory") LocalContainerEntityManagerFactoryBean oracleEntityManagerFactory) {
        return new JpaTransactionManager(oracleEntityManagerFactory.getObject());
    }
}
