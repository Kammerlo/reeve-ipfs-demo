package org.cardanofoundation.reeve_ipfs_publisher.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.reeve_ipfs_publisher.domain.requests.PublishMessageRequest;
import org.cardanofoundation.reeve_ipfs_publisher.service.PublisherService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaConsumer {

    private final PublisherService publisherService;

    @KafkaListener(topics = "${reeve.kafka.topic}")
    public void list(PublishMessageRequest request) {
        log.info("Received message from IPFS: {}", request);
        publisherService.publishMessage(request);
    }

}
