package org.cardanofoundation.reeve_ipfs_publisher.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class BlockfrostIpfsResponse {

    private String name;
    @JsonProperty("ipfs_hash")
    private String ipfsHash;
    private String size;

}
