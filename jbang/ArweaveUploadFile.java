///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import java.math.BigInteger;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.*;

public class ArweaveUploadFile {

    static final String GATEWAY = System.getenv().getOrDefault("GATEWAY", "http://localhost:1984"); // ArLocal

    static final Base64.Encoder B64    = Base64.getUrlEncoder().withoutPadding();
    static final Base64.Decoder B64D   = Base64.getUrlDecoder();
    static final int MAX_CHUNK_SIZE    = 256 * 1024; // 262144 bytes
    static final int MIN_CHUNK_SIZE    = 32  * 1024; //  32768 bytes
    static final int NOTE_SIZE         = 32;          // intToBuffer width

    // ── Node record for Merkle tree ──────────────────────────────────────────
    record MNode(byte[] id, int maxByteRange) {}

    // =========================================================================
    // main
    // =========================================================================
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: jbang ArweaveUploadFile.java <wallet.json> <file> [content-type]");
            System.exit(1);
        }
        String walletFile  = args[0];
        String uploadFile  = args[1];
        String contentType = args.length > 2 ? args[2] : detectContentType(uploadFile);

        System.out.println("🌐 ArLocal          : " + GATEWAY);
        System.out.println("📂 Wallet           : " + walletFile);
        System.out.println("📄 File             : " + uploadFile);
        System.out.println("📦 Content-Type     : " + contentType);
        System.out.println();

        // Load wallet
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jwk        = mapper.readTree(Paths.get(walletFile).toFile());
        RSAPrivateCrtKey pk = jwkToPrivateKey(jwk);
        String owner        = jwk.get("n").asText();
        String address      = deriveAddress(owner);
        System.out.println("📬 Wallet address   : " + address);

        // Read data
        byte[] data    = Files.readAllBytes(Paths.get(uploadFile));
        String dataSize = String.valueOf(data.length);
        System.out.println("📏 File size        : " + data.length + " bytes");

        // Compute data_root via Arweave Merkle tree
        byte[] dataRootBytes = merkleRoot(data);
        String dataRoot      = B64.encodeToString(dataRootBytes);
        System.out.println("🌳 data_root        : " + dataRoot);

        // Fetch anchor + price
        String lastTx = fetchLastTx(address);
        System.out.println("⚓ Last TX           : " + (lastTx.isEmpty() ? "(none)" : lastTx));
        long price = fetchPrice(data.length, address);
        System.out.printf("💸 Price            : %d Winston%n", price);

        // Compute signing data (format-2 deepHash)
        byte[] ownerBytes  = B64D.decode(owner);
        byte[] lastTxBytes = lastTx.isEmpty() ? new byte[0] : B64D.decode(lastTx);
        byte[] sigData     = deepHashTx2(
            ownerBytes, new byte[0],
            "0", String.valueOf(price), lastTxBytes,
            new byte[][]{ "Content-Type".getBytes() },
            new byte[][]{ contentType.getBytes() },
            dataSize, dataRootBytes);

        // Sign
        byte[] sig   = sign(sigData, pk);
        String txId  = B64.encodeToString(sha256(sig));
        System.out.println("✍️  TX ID            : " + txId);

        // Build tags
        ArrayNode tags = mapper.createArrayNode();
        ObjectNode tag = mapper.createObjectNode();
        tag.put("name",  B64.encodeToString("Content-Type".getBytes()));
        tag.put("value", B64.encodeToString(contentType.getBytes()));
        tags.add(tag);

        // Build tx JSON
        ObjectNode tx = mapper.createObjectNode();
        tx.put("format",    2);
        tx.put("id",        txId);
        tx.put("last_tx",   lastTx);
        tx.put("owner",     owner);
        tx.set("tags",      tags);
        tx.put("target",    "");
        tx.put("quantity",  "0");
        tx.put("data",      B64.encodeToString(data));
        tx.put("data_size", dataSize);
        tx.put("data_root", dataRoot);
        tx.put("reward",    String.valueOf(price));
        tx.put("signature", B64.encodeToString(sig));

        // POST
        System.out.println("\n🚀 Uploading...");
        int status = postTx(mapper.writeValueAsString(tx));
        if (status != 200 && status != 202) {
            System.err.println("❌ Upload failed — HTTP " + status);
            System.exit(1);
        }
        System.out.println("📨 Accepted (HTTP " + status + ")");

        // Mine
        System.out.println("⛏️  Mining...");
        mine();
        System.out.println("✅ Confirmed!");
        System.out.println("🔗 " + GATEWAY + "/" + txId);
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
        // 1. Chunk the data into ranges [min, max)
        List<int[]> ranges = chunkRanges(data.length);

        // 2. Build leaf nodes
        List<MNode> leaves = new ArrayList<>();
        for (int[] r : ranges) {
            byte[] chunk    = Arrays.copyOfRange(data, r[0], r[1]);
            byte[] dataHash = sha256(chunk);
            // leaf.id = SHA256( SHA256(dataHash) ‖ SHA256(intToBuffer(maxByteRange)) )
            byte[] leafId   = sha256(cat(sha256(dataHash), sha256(intToBuffer(r[1]))));
            leaves.add(new MNode(leafId, r[1]));
        }

        // 3. Build tree layers up to root
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
        out.add(new int[]{ cursor, cursor + left }); // last (or only) chunk
        return out;
    }

    /** Build Merkle layers recursively until one node remains (the root) */
    static MNode buildLayers(List<MNode> nodes) throws Exception {
        if (nodes.size() == 1) return nodes.get(0);

        List<MNode> next = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i += 2) {
            MNode left = nodes.get(i);
            if (i + 1 >= nodes.size()) {
                next.add(left); // odd node — promote unchanged
            } else {
                MNode right = nodes.get(i + 1);
                // branch.id = SHA256( SHA256(left.id) ‖ SHA256(right.id)
                //                   ‖ SHA256(intToBuffer(left.maxByteRange)) )
                byte[] branchId = sha256(cat(
                    cat(sha256(left.id), sha256(right.id)),
                    sha256(intToBuffer(left.maxByteRange))
                ));
                next.add(new MNode(branchId, right.maxByteRange));
            }
        }
        return buildLayers(next);
    }

    /** 32-byte big-endian encoding — mirrors arweave-js intToBuffer() (NOTE_SIZE = 32) */
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
    //
    // blob(d)  = SHA384( SHA384("blob" ‖ len(d)) ‖ SHA384(d) )
    // list(xs) = fold xs with acc₀=SHA384("list"‖count):
    //              acc_{i+1} = SHA384(acc_i ‖ deepHash(xs[i]))
    // =========================================================================

    /** Format-2 transaction signing payload */
    static byte[] deepHashTx2(
            byte[] owner, byte[] target,
            String quantity, String reward, byte[] lastTx,
            byte[][] tagNames, byte[][] tagValues,
            String dataSize, byte[] dataRoot) throws Exception {

        // Each tag is a 2-element list [name_bytes, value_bytes]
        byte[][] tagPairHashes = new byte[tagNames.length][];
        for (int i = 0; i < tagNames.length; i++) {
            tagPairHashes[i] = deepHashList(new byte[][]{ tagNames[i], tagValues[i] });
        }
        // The tag list itself is a list of those pair-hashes
        byte[] tagListHash = deepHashOfHashes(tagPairHashes);

        // Outer 9-element list
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
        byte[] acc = sha384(cat("list".getBytes(),
                String.valueOf(blobs.length).getBytes()));
        for (byte[] b : blobs) acc = sha384(cat(acc, deepHashBlob(b)));
        return acc;
    }

    /** deepHash of a list where children are already deepHash outputs */
    static byte[] deepHashOfHashes(byte[][] hashes) throws Exception {
        byte[] acc = sha384(cat("list".getBytes(),
                String.valueOf(hashes.length).getBytes()));
        for (byte[] h : hashes) acc = sha384(cat(acc, h));
        return acc;
    }

    /** deepHash of a single blob: SHA384("blob"‖len) → SHA384(above ‖ SHA384(data)) */
    static byte[] deepHashBlob(byte[] data) throws Exception {
        byte[] tag = sha384(cat("blob".getBytes(),
                String.valueOf(data.length).getBytes()));
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
    // Network
    // =========================================================================
    static void mine() throws Exception {
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder().uri(URI.create(GATEWAY + "/mine")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }
    static int postTx(String json) throws Exception {
        HttpResponse<String> r = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder()
                .uri(URI.create(GATEWAY + "/tx"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        System.out.println("📡 Response         : " + r.body());
        return r.statusCode();
    }
    static String fetchLastTx(String addr) throws Exception {
        try (var in = URI.create(GATEWAY + "/wallet/" + addr + "/last_tx")
                         .toURL().openStream()) {
            return new String(in.readAllBytes()).trim();
        } catch (Exception e) { return ""; }
    }
    static long fetchPrice(int size, String addr) throws Exception {
        try (var in = URI.create(GATEWAY + "/price/" + size + "/" + addr)
                         .toURL().openStream()) {
            return Long.parseLong(new String(in.readAllBytes()).trim());
        }
    }

    // =========================================================================
    // Wallet helpers
    // =========================================================================
    static String deriveAddress(String n) throws Exception {
        return B64.encodeToString(sha256(B64D.decode(n)));
    }
    static RSAPrivateCrtKey jwkToPrivateKey(JsonNode j) throws Exception {
        BigInteger n  = new BigInteger(1, B64D.decode(j.get("n").asText()));
        BigInteger e  = new BigInteger(1, B64D.decode(j.get("e").asText()));
        BigInteger d  = new BigInteger(1, B64D.decode(j.get("d").asText()));
        BigInteger p  = new BigInteger(1, B64D.decode(j.get("p").asText()));
        BigInteger q  = new BigInteger(1, B64D.decode(j.get("q").asText()));
        BigInteger dp = new BigInteger(1, B64D.decode(j.get("dp").asText()));
        BigInteger dq = new BigInteger(1, B64D.decode(j.get("dq").asText()));
        BigInteger qi = new BigInteger(1, B64D.decode(j.get("qi").asText()));
        return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new RSAPrivateCrtKeySpec(n, e, d, p, q, dp, dq, qi));
    }
    static String detectContentType(String f) {
        f = f.toLowerCase();
        if (f.endsWith(".png"))                        return "image/png";
        if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return "image/jpeg";
        if (f.endsWith(".gif"))                        return "image/gif";
        if (f.endsWith(".pdf"))                        return "application/pdf";
        if (f.endsWith(".html"))                       return "text/html";
        if (f.endsWith(".json"))                       return "application/json";
        if (f.endsWith(".mp4"))                        return "video/mp4";
        return "text/plain";
    }
}