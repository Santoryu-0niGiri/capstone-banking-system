
package com.capstone.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * The default single-DataSource JPA autoconfiguration is disabled here
 * because this service deliberately wires two independent persistence
 * units (Oracle for account balances, PostgreSQL for the ledger audit
 * trail) — see config.OracleDataSourceConfig and config.PostgresDataSourceConfig.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.capstone.transaction", "com.capstone.common"})
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}

