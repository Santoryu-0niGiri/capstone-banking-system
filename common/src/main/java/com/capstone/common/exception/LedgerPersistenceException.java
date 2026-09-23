
package com.capstone.common.exception;

/**
 * Thrown when the PostgreSQL ledger-audit write of a transaction fails after
 * the Oracle balance mutation succeeded. The caller of the service that
 * throws this exception is guaranteed that the corresponding Oracle balance
 * mutation has already been (or is being) compensated/rolled back.
 */
public class LedgerPersistenceException extends RuntimeException {

    public LedgerPersistenceException(String message) {
        super(message);
    }

    public LedgerPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

