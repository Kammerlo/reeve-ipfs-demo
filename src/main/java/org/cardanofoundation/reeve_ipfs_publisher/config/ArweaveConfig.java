package org.cardanofoundation.reeve_ipfs_publisher.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cardanofoundation.reeve_ipfs_publisher.domain.dto.ArweaveWallet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Base64;

@Configuration
@ConditionalOnProperty(prefix = "reeve.arweave", value = "enabled", havingValue = "true", matchIfMissing = true)
public class ArweaveConfig {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    static final Base64.Decoder B64D   = Base64.getUrlDecoder();

    @Bean
    public ArweaveWallet arweaveWallet(@Value("${reeve.arweave.wallet.key-file}") String walletFile, @Value("${reeve.arweave.gateway}") String gateway, ObjectMapper objectMapper) throws Exception {
        JsonNode jwk = objectMapper.readTree(Paths.get(walletFile).toFile());
        RSAPrivateCrtKey pk = ArweaveConfig.jwkToPrivateKey(jwk);
        String owner = jwk.get("n").asText();
        String address = ArweaveConfig.deriveAddress(owner);
        return ArweaveWallet.builder()
                .owner(owner)
                .address(address)
                .privateKey(pk)
                .gateway(gateway)
                .build();
    }

    static String deriveAddress(String n) throws Exception {
        return B64.encodeToString(sha256(B64D.decode(n)));
    }

    static byte[] sha256(byte[] d) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(d);
    }

    static RSAPrivateCrtKey jwkToPrivateKey(JsonNode j) throws Exception {
        BigInteger n  = new BigInteger(1, B64D.decode(j.get("n").asText()));
        BigInteger e  = new BigInteger(1, B64D.decode(j.get("e").asText()));
        BigInteger d  = new BigInteger(1, B64D.decode(j.get("d").asText()));
        BigInteger p  = new BigInteger(1, B64D.decode(j.get("p").asText()));
        BigInteger q  = new BigInteger(1, B64D.decode(j.get("q").asText()));
        BigInteger dp = new BigInteger(1, B64D.decode(j.get("dp").asText()));
        BigInteger dq = new BigInteger(1, B64D.decode(j.get("dq").asText()));
        BigInteger qi = new BigInteger(1, B64D.decode(j.get("qi").asText()));
        return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new RSAPrivateCrtKeySpec(n, e, d, p, q, dp, dq, qi));
    }

}
