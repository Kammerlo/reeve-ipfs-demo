package org.cardanofoundation.reeve_ipfs_publisher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.reeve_ipfs_publisher.domain.dto.ArweaveWallet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "reeve.arweave", value = "enabled", havingValue = "true", matchIfMissing = true)
public class ArweaveService {

    private final ArweaveWallet arweaveWallet;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder B64D = Base64.getUrlDecoder();

    private static final int MAX_CHUNK_SIZE = 256 * 1024; // 262144 bytes
    private static final int MIN_CHUNK_SIZE = 32  * 1024; //  32768 bytes
    private static final int NOTE_SIZE      = 32;          // intToBuffer width

    // ── Node record for Merkle tree ──────────────────────────────────────────
    record MNode(byte[] id, int maxByteRange) {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Serialises {@code content} as UTF-8, builds an Arweave format-2 transaction,
     * signs it with the configured wallet, uploads it and returns the transaction ID.
     *
     * @return the base64url-encoded transaction ID, or {@code null} on failure.
     */
    public String storeInArweave(String content) {
        try {
            byte[] data      = content.getBytes();
            String dataSize  = String.valueOf(data.length);
            String gateway   = arweaveWallet.getGateway();

            // Compute Arweave Merkle data_root
            byte[] dataRootBytes = merkleRoot(data);
            String dataRoot      = B64.encodeToString(dataRootBytes);
            log.debug("data_root: {}", dataRoot);

            // Fetch anchor + price
            String lastTx      = fetchLastTx(arweaveWallet.getAddress(), gateway);
            long   price       = fetchPrice(data.length, arweaveWallet.getAddress(), gateway);
            log.debug("last_tx: {}, price: {} Winston", lastTx.isEmpty() ? "(none)" : lastTx, price);

            // Build signing payload (format-2 deepHash)
            byte[] ownerBytes  = B64D.decode(arweaveWallet.getOwner());
            byte[] lastTxBytes = lastTx.isEmpty() ? new byte[0] : B64D.decode(lastTx);
            byte[] sigData     = deepHashTx2(
                    ownerBytes, new byte[0],
                    "0", String.valueOf(price), lastTxBytes,
                    new byte[][]{ "Content-Type".getBytes() },
                    new byte[][]{ "application/json".getBytes() },
                    dataSize, dataRootBytes);

            // Sign & derive TX ID
            byte[] sig  = sign(sigData, arweaveWallet.getPrivateKey());
            String txId = B64.encodeToString(sha256(sig));
            log.info("Arweave TX ID: {}", txId);

            // Build tags
            ArrayNode tags = objectMapper.createArrayNode();
            ObjectNode tag = objectMapper.createObjectNode();
            tag.put("name",  B64.encodeToString("Content-Type".getBytes()));
            tag.put("value", B64.encodeToString("application/json".getBytes()));
            tags.add(tag);

            // Assemble format-2 transaction
            ObjectNode tx = objectMapper.createObjectNode();
            tx.put("format",    2);
            tx.put("id",        txId);
            tx.put("last_tx",   lastTx);
            tx.put("owner",     arweaveWallet.getOwner());
            tx.set("tags",      tags);
            tx.put("target",    "");
            tx.put("quantity",  "0");
            tx.put("data",      B64.encodeToString(data));
            tx.put("data_size", dataSize);
            tx.put("data_root", dataRoot);
            tx.put("reward",    String.valueOf(price));
            tx.put("signature", B64.encodeToString(sig));

            // POST transaction
            int status = postTx(objectMapper.writeValueAsString(tx), gateway);
            if (status != 200 && status != 202) {
                log.error("Arweave upload failed — HTTP {}", status);
                return null;
            }
            log.info("Arweave upload accepted (HTTP {}): {}/{}", status, gateway, txId);

            // Trigger mining (relevant for ArLocal / testnet)
            mine(gateway);

            return txId;

        } catch (Exception e) {
            log.error("Error storing content in Arweave: {}", e.getMessage(), e);
            return null;
        }
    }

    // =========================================================================
    // ARWEAVE MERKLE TREE — mirrors merkle.ts exactly
    //
    // leaf.id   = SHA256( SHA256(chunkData) ‖ SHA256(intToBuffer(maxByteRange)) )
    // branch.id = SHA256( SHA256(left.id)  ‖ SHA256(right.id)
    //                   ‖ SHA256(intToBuffer(left.maxByteRange)) )
    // intToBuffer(n) = 32-byte big-endian unsigned integer
    // =========================================================================
    static byte[] merkleRoot(byte[] data) throws Exception {
        List<int[]> ranges = chunkRanges(data.length);

        List<MNode> leaves = new ArrayList<>();
        for (int[] r : ranges) {
            byte[] chunk    = Arrays.copyOfRange(data, r[0], r[1]);
            byte[] dataHash = sha256(chunk);
            byte[] leafId   = sha256(cat(sha256(dataHash), sha256(intToBuffer(r[1]))));
            leaves.add(new MNode(leafId, r[1]));
        }
        return buildLayers(leaves).id;
    }

    /** Produce [minByteRange, maxByteRange] pairs exactly as arweave-js chunkData() */
    static List<int[]> chunkRanges(int total) {
        List<int[]> out    = new ArrayList<>();
        int         cursor = 0;
        int         left   = total;

        while (left >= MAX_CHUNK_SIZE) {
            int size = MAX_CHUNK_SIZE;
            int next = left - MAX_CHUNK_SIZE;
            if (next > 0 && next < MIN_CHUNK_SIZE) {
                size = (int) Math.ceil(left / 2.0);
            }
            out.add(new int[]{ cursor, cursor + size });
            cursor += size;
            left   -= size;
        }
        out.add(new int[]{ cursor, cursor + left });
        return out;
    }

    /** Build Merkle layers recursively until one node remains (the root) */
    static MNode buildLayers(List<MNode> nodes) throws Exception {
        if (nodes.size() == 1) return nodes.get(0);

        List<MNode> next = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i += 2) {
            MNode left = nodes.get(i);
            if (i + 1 >= nodes.size()) {
                next.add(left);
            } else {
                MNode right     = nodes.get(i + 1);
                byte[] branchId = sha256(cat(
                        cat(sha256(left.id), sha256(right.id)),
                        sha256(intToBuffer(left.maxByteRange))
                ));
                next.add(new MNode(branchId, right.maxByteRange));
            }
        }
        return buildLayers(next);
    }

    /** 32-byte big-endian encoding — mirrors arweave-js intToBuffer() */
    static byte[] intToBuffer(int n) {
        byte[] buf = new byte[NOTE_SIZE];
        for (int i = NOTE_SIZE - 1; i >= 0; i--) {
            buf[i] = (byte)(n & 0xFF);
            n >>>= 8;
        }
        return buf;
    }

    // =========================================================================
    // DEEP HASH — mirrors arweave-js deepHash.ts (all hashes are SHA-384)
    // =========================================================================

    /** Format-2 transaction signing payload */
    static byte[] deepHashTx2(
            byte[] owner, byte[] target,
            String quantity, String reward, byte[] lastTx,
            byte[][] tagNames, byte[][] tagValues,
            String dataSize, byte[] dataRoot) throws Exception {

        byte[][] tagPairHashes = new byte[tagNames.length][];
        for (int i = 0; i < tagNames.length; i++) {
            tagPairHashes[i] = deepHashList(new byte[][]{ tagNames[i], tagValues[i] });
        }
        byte[] tagListHash = deepHashOfHashes(tagPairHashes);

        byte[][] items = {
                "2".getBytes(), owner, target,
                quantity.getBytes(), reward.getBytes(), lastTx,
                null,               // slot 6 = tag list (special)
                dataSize.getBytes(), dataRoot
        };
        byte[] acc = sha384(cat("list".getBytes(), "9".getBytes()));
        for (int i = 0; i < 9; i++) {
            byte[] h = (i == 6) ? tagListHash : deepHashBlob(items[i]);
            acc = sha384(cat(acc, h));
        }
        return acc;
    }

    /** deepHash of a list of raw blobs */
    static byte[] deepHashList(byte[][] blobs) throws Exception {
        byte[] acc = sha384(cat("list".getBytes(), String.valueOf(blobs.length).getBytes()));
        for (byte[] b : blobs) acc = sha384(cat(acc, deepHashBlob(b)));
        return acc;
    }

    /** deepHash of a list where children are already deepHash outputs */
    static byte[] deepHashOfHashes(byte[][] hashes) throws Exception {
        byte[] acc = sha384(cat("list".getBytes(), String.valueOf(hashes.length).getBytes()));
        for (byte[] h : hashes) acc = sha384(cat(acc, h));
        return acc;
    }

    /** deepHash of a single blob: SHA384("blob"‖len) → SHA384(above ‖ SHA384(data)) */
    static byte[] deepHashBlob(byte[] data) throws Exception {
        byte[] tag = sha384(cat("blob".getBytes(), String.valueOf(data.length).getBytes()));
        return sha384(cat(tag, sha384(data)));
    }

    // =========================================================================
    // Crypto
    // =========================================================================
    static byte[] sha256(byte[] d) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(d);
    }

    static byte[] sha384(byte[] d) throws Exception {
        return MessageDigest.getInstance("SHA-384").digest(d);
    }

    static byte[] cat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0,        a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    static byte[] sign(byte[] data, RSAPrivateCrtKey key) throws Exception {
        Signature s = Signature.getInstance("RSASSA-PSS");
        s.setParameter(new PSSParameterSpec(
                "SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"), 32, 1));
        s.initSign(key);
        s.update(data);
        return s.sign();
    }

    // =========================================================================
    // Network helpers (gateway-aware)
    // =========================================================================
    static void mine(String gateway) throws Exception {
        HttpClient.newHttpClient().send(
                HttpRequest.newBuilder().uri(URI.create(gateway + "/mine")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static int postTx(String json, String gateway) throws Exception {
        HttpResponse<String> r = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create(gateway + "/tx"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return r.statusCode();
    }

    static String fetchLastTx(String addr, String gateway) throws Exception {
        try (var in = URI.create(gateway + "/wallet/" + addr + "/last_tx").toURL().openStream()) {
            return new String(in.readAllBytes()).trim();
        } catch (Exception e) {
            return "";
        }
    }

    static long fetchPrice(int size, String addr, String gateway) throws Exception {
        try (var in = URI.create(gateway + "/price/" + size + "/" + addr).toURL().openStream()) {
            return Long.parseLong(new String(in.readAllBytes()).trim());
        }
    }
}
