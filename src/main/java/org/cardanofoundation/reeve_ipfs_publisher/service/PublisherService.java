package org.cardanofoundation.reeve_ipfs_publisher.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.cardanofoundation.reeve_ipfs_publisher.domain.requests.PublishMessageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublisherService {

    private final IpfsService ipfsService;
    private final CardanoService cardanoService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void publishMessage(PublishMessageRequest request) {

        String hash = ipfsService.storeInIpfs(convertToJson(request));
        cardanoService.publishIpfsHash(List.of(hash));
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
