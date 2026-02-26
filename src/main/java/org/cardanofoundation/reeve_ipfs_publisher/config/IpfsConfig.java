package org.cardanofoundation.reeve_ipfs_publisher.config;

import io.ipfs.api.IPFS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IpfsConfig {

    @Bean
    public IPFS ipfs(@Value("${reeve.ipfs.node}") String nodeUrl) {
        return new IPFS(nodeUrl);
    }
}
