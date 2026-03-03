package org.cardanofoundation.reeve_ipfs_publisher.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ArweaveWallet {

    private RSAPrivateCrtKey privateKey;
    private String owner;
    private String address;
    private String gateway;

}
