package org.cardanofoundation.reeve_ipfs_publisher.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "reeve.ipfs.blockfrost")
@ConditionalOnProperty(prefix = "reeve.ipfs.blockfrost", value = "enabled", havingValue = "true", matchIfMissing = true)
@Getter
@Setter
public class BlockfrostProperties {

    private String url;
    private String projectId;

}
