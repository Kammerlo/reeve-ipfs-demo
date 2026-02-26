package org.cardanofoundation.reeve_ipfs_publisher.service;

import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import lombok.RequiredArgsConstructor;
import io.ipfs.api.IPFS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class IpfsService {

    private final IPFS ipfs;

    public String storeInIpfs(String message) {
        try {
            // Convert string to a streamable format for IPFS
            NamedStreamable.ByteArrayWrapper file = new NamedStreamable.ByteArrayWrapper(message.getBytes());

            // Add the content to IPFS
            MerkleNode response = ipfs.add(file).get(0);

            // Return the CID (Hash)
            String base58 = response.hash.toBase58();
            log.info("Content stored in IPFS with CID: {}", base58);
            return base58;

        } catch (IOException e) {
            throw new RuntimeException("Error while saving to IPFS", e);
        }
    }
}
