package org.cardanofoundation.reeve_ipfs_publisher.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class TransactionItem {

    private String id;
    private BigDecimal amount;
    private BigDecimal fxRate;
    private Document document;
    private Event event;
    private CostCenter costCenter;

}
