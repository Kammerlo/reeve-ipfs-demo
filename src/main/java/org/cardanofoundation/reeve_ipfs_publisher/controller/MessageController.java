package org.cardanofoundation.reeve_ipfs_publisher.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.reeve_ipfs_publisher.domain.requests.PublishMessageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    @Value("${reeve.ipfs.topic}")
    private String topic;

    @PostMapping("/publish")
    public String publishMessage(@RequestBody PublishMessageRequest request) {
        log.info("Received message to publish: {}", request);
        kafkaTemplate.send(topic, request);
        return "Message published to IPFS successfully!";
    }
}
