package org.cardanofoundation.reeve_ipfs_publisher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import lombok.RequiredArgsConstructor;
import io.ipfs.api.IPFS;
import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.reeve_ipfs_publisher.config.BlockfrostProperties;
import org.cardanofoundation.reeve_ipfs_publisher.domain.dto.BlockfrostIpfsResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "reeve.ipfs", value = "enabled", havingValue = "true", matchIfMissing = true)
public class IpfsService {

    private final Optional<IPFS> ipfs;
    private final Optional<BlockfrostProperties> blockfrostProperties;


    public String storeInIpfs(String message) {
        if(ipfs.isPresent()) {
            return localIpfsNode(message);
        }
        if(blockfrostProperties.isPresent()) {
            try {
                return blockfrostIpfs(message);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        throw  new IllegalStateException("No IPFS Adapter found");
    }

    private String blockfrostIpfs(String message) throws IOException, InterruptedException {
        BlockfrostProperties properties = blockfrostProperties.get();

        String boundary = "----JavaBoundary" + UUID.randomUUID();
        // Build multipart body
        String partHeader =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"reeve.json\"\r\n" +
                        "Content-Type: application/octet-stream\r\n\r\n";

        String endBoundary = "\r\n--" + boundary + "--\r\n";

        byte[] body = concat(
                partHeader.getBytes(),
                message.getBytes(),
                endBoundary.getBytes()
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getUrl()))
                .header("project_id", properties.getProjectId())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
        ObjectMapper mapper = new ObjectMapper();
        BlockfrostIpfsResponse responseObject = mapper.readValue(response.body(), BlockfrostIpfsResponse.class);
        return responseObject.getIpfsHash();
    }

    private static byte[] concat(byte[]... arrays) {
        int length = 0;
        for (byte[] array : arrays) {
            length += array.length;
        }

        byte[] result = new byte[length];
        int pos = 0;

        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, pos, array.length);
            pos += array.length;
        }

        return result;
    }

    private String localIpfsNode(String message) {
        try {
            // Convert string to a streamable format for IPFS
            NamedStreamable.ByteArrayWrapper file = new NamedStreamable.ByteArrayWrapper(message.getBytes());

            // Add the content to IPFS
            MerkleNode response = ipfs.get().add(file).get(0);

            // Return the CID (Hash)
            String base58 = response.hash.toBase58();
            log.info("Content stored in IPFS with CID: {}", base58);
            return base58;

        } catch (IOException e) {
            throw new RuntimeException("Error while saving to IPFS", e);
        }
    }
}
