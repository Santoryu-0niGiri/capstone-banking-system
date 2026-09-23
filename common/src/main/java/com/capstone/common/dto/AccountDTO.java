package com.capstone.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountDTO(Long acctNo, Long custId, String acctType, String acctStatus,
                          BigDecimal balance, LocalDateTime createdAt, Long version) {
}
