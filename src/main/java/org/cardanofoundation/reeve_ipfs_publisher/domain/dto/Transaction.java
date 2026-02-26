package org.cardanofoundation.reeve_ipfs_publisher.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class Transaction {

    private String id;
    private String date;
    private String type;
    private String number;
    private String batchId;
    private String accountingPeriod;
    private List<TransactionItem> items;
}
