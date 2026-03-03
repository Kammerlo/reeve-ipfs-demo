package org.cardanofoundation.reeve_ipfs_publisher.config;

import io.ipfs.api.IPFS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "reeve.ipfs.local", value = "enabled", havingValue = "true", matchIfMissing = true)
public class IpfsConfig {

    @Bean
    public IPFS ipfs(@Value("${reeve.ipfs.local.node}") String nodeUrl) {
        return new IPFS(nodeUrl);
    }
}
