
package com.capstone.transaction.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.HashMap;
import java.util.Map;

/**
<<<<<<< Updated upstream
 * Primary persistence unit: Oracle XE, holding the `account` table.
 * Marked @Primary so plain @Autowired DataSource/EntityManagerFactory
 * injections elsewhere in the app default to Oracle.
=======
 * Primary persistence unit: Oracle XE (CUSTOMER_BALANCE_MASTER +
 * TRANSACTION_MASTER).
 * Marked @Primary so unqualified DataSource/EntityManagerFactory injections
 * default here.
 * HikariCP pool is configured explicitly because
 * DataSourceProperties.initializeDataSourceBuilder()
 * does not bind nested hikari.* sub-keys from a custom prefix.
>>>>>>> Stashed changes
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.capstone.transaction.repository.oracle", entityManagerFactoryRef = "oracleEntityManagerFactory", transactionManagerRef = "oracleTransactionManager")
public class OracleDataSourceConfig {

    @Primary
    @Bean(name = "oracleDataSourceProperties")
    @ConfigurationProperties("spring.datasource.oracle")
    public DataSourceProperties oracleDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "oracleDataSource")
    public DataSource oracleDataSource(@Qualifier("oracleDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean(name = "oracleEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean oracleEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("oracleDataSource") DataSource dataSource) {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", "update");
        props.put("hibernate.dialect", "org.hibernate.dialect.OracleDialect");
        return builder
                .dataSource(dataSource)
                .packages("com.capstone.transaction.entity.oracle")   // CustomerBalanceMaster + TransactionMaster
                .persistenceUnit("oracle")
                .properties(props)
                .build();
    }

    @Primary
    @Bean(name = "oracleTransactionManager")
    public PlatformTransactionManager oracleTransactionManager(
            @Qualifier("oracleEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
<<<<<<< Updated upstream
}

=======
}
>>>>>>> Stashed changes
