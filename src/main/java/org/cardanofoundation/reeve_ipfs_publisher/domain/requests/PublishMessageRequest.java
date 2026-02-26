package org.cardanofoundation.reeve_ipfs_publisher.domain.requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cardanofoundation.reeve_ipfs_publisher.domain.dto.Transaction;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class PublishMessageRequest {

    private List<Transaction> transaction;

}
