package org.cardanofoundation.reeve_ipfs_publisher.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.reeve_ipfs_publisher.config.IagonProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "reeve.iagon", name = "enabled", havingValue = "true", matchIfMissing = false)
public class IagonService {

    private final IagonProperties iagonProperties;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Uploads the given JSON string as a file to Iagon decentralised storage.
     *
     * @param jsonData the JSON content to upload
     * @return the Iagon file ID (data._id) of the uploaded file
     * @throws IagonUploadException if the upload fails or the response cannot be parsed
     */
    public String storeInIagon(String jsonData) {
        String boundary = "----IagonBoundary" + UUID.randomUUID().toString().replace("-", "");
        String filename = "reeve-" + UUID.randomUUID() + ".json";
        byte[] body = buildMultipartBody(boundary, filename, jsonData);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(iagonProperties.getUrl()))
                .header("x-api-key", iagonProperties.getToken())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                log.error("Iagon upload failed. HTTP {}: {}", response.statusCode(), response.body());
                throw new IagonUploadException(
                        "Iagon upload failed with HTTP status " + response.statusCode() + ": " + response.body());
            }
            String fileId = parseFileId(response.body());
            log.info("Successfully uploaded file to Iagon. File ID: {}", fileId);
            return fileId;

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IagonUploadException("Iagon upload request failed", e);
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Builds a {@code multipart/form-data} body containing:
     * <ul>
     *   <li>{@code file}       – the JSON payload as binary content</li>
     *   <li>{@code filename}   – the logical filename stored on Iagon</li>
     *   <li>{@code visibility} – taken from {@link IagonProperties}</li>
     * </ul>
     */
    private byte[] buildMultipartBody(String boundary, String filename, String jsonData) {
        String CRLF = "\r\n";
        String dash = "--";

        List<byte[]> parts = new ArrayList<>();

        // -- file part ---------------------------------------------------------
        parts.add((dash + boundary + CRLF
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"" + CRLF
                + "Content-Type: application/json" + CRLF
                + CRLF).getBytes(StandardCharsets.UTF_8));
        parts.add(jsonData.getBytes(StandardCharsets.UTF_8));
        parts.add(CRLF.getBytes(StandardCharsets.UTF_8));

        // -- filename part -----------------------------------------------------
        parts.add((dash + boundary + CRLF
                + "Content-Disposition: form-data; name=\"filename\"" + CRLF
                + CRLF
                + filename + CRLF).getBytes(StandardCharsets.UTF_8));

        // -- visibility part ---------------------------------------------------
        String visibility = "public";

        parts.add((dash + boundary + CRLF
                + "Content-Disposition: form-data; name=\"visibility\"" + CRLF
                + CRLF
                + visibility + CRLF).getBytes(StandardCharsets.UTF_8));

        // -- closing boundary --------------------------------------------------
        parts.add((dash + boundary + dash + CRLF).getBytes(StandardCharsets.UTF_8));

        // Concatenate all byte arrays
        int totalLength = parts.stream().mapToInt(b -> b.length).sum();
        byte[] body = new byte[totalLength];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, body, offset, part.length);
            offset += part.length;
        }
        return body;
    }

    /**
     * Extracts the file ID from an Iagon upload response.
     * Expected JSON structure: {@code { "success": true, "data": { "_id": "<id>" } }}
     */
    private String parseFileId(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode fileId = root.path("data").path("_id");
            if (fileId.isMissingNode() || fileId.isNull()) {
                log.error("Unexpected Iagon response – '_id' missing: {}", responseBody);
                throw new IagonUploadException("Could not parse file ID from Iagon response: " + responseBody);
            }
            return fileId.asText();
        } catch (IOException e) {
            throw new IagonUploadException("Failed to parse Iagon upload response", e);
        }
    }

    // ---------------------------------------------------------------------------
    // Exception
    // ---------------------------------------------------------------------------

    public static class IagonUploadException extends RuntimeException {
        public IagonUploadException(String message) {
            super(message);
        }
        public IagonUploadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}