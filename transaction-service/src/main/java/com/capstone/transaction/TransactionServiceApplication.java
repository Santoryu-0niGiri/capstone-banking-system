
package com.capstone.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Transaction Service Application.
 * Dual persistence units (Oracle for account balances, PostgreSQL for the ledger audit
 * trail) are configured via OracleDataSourceConfig and PostgresDataSourceConfig.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.capstone.transaction", "com.capstone.common"})
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}

