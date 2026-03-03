package org.cardanofoundation.reeve_ipfs_publisher.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.cardanofoundation.reeve_ipfs_publisher.domain.requests.PublishMessageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PublisherService {

    private final Optional<IpfsService> ipfsService;
    private final Optional<ArweaveService> arweaveService;
    private final CardanoService cardanoService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void publishMessage(PublishMessageRequest request) {
        List<String> ipfsHashes = new ArrayList<>();
        List<String> arweaveHashes = new ArrayList<>();
        if(ipfsService.isPresent()) {
            ipfsHashes.add(ipfsService.get().storeInIpfs(convertToJson(request)));
        }
        if(arweaveService.isPresent()) {
            arweaveHashes.add(arweaveService.get().storeInArweave(convertToJson(request)));
        }

        cardanoService.publishHashes(ipfsHashes, arweaveHashes);
    }

    public String convertToJson(Object object) {
        try {
            // This method performs the conversion
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            // Handle cases where the object cannot be serialized
            throw new RuntimeException("Failed to convert object to JSON", e);
        }
    }

}
