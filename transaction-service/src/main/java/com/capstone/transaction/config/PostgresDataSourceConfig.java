
package com.capstone.transaction.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * Secondary persistence unit: PostgreSQL, holding the `ledger_mutation_audit`
 * table. Kept fully independent from the Oracle unit above -- this is what
 * makes the dual-write compensating-transaction pattern in
 * TransactionService necessary (there is no single JTA transaction
 * spanning both databases).
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.capstone.transaction.repository.postgres",
        entityManagerFactoryRef = "postgresEntityManagerFactory",
        transactionManagerRef = "postgresTransactionManager"
)
public class PostgresDataSourceConfig {

    @Bean(name = "postgresDataSourceProperties")
    @ConfigurationProperties("spring.datasource.postgres")
    public DataSourceProperties postgresDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "postgresDataSource")
    public DataSource postgresDataSource(@Qualifier("postgresDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "postgresEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean postgresEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("postgresDataSource") DataSource dataSource) {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", "validate");
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        return builder
                .dataSource(dataSource)
                .packages("com.capstone.transaction.entity.postgres")
                .persistenceUnit("postgres")
                .properties(props)
                .build();
    }

    @Bean(name = "postgresTransactionManager")
    public PlatformTransactionManager postgresTransactionManager(
            @Qualifier("postgresEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}

