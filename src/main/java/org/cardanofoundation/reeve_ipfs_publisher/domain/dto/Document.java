package org.cardanofoundation.reeve_ipfs_publisher.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class Document {

    private String Number;
    private Vat vat;
    private Currency currency;
}
