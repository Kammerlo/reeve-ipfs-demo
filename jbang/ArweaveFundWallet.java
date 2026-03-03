///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Base64;

public class ArweaveFundWallet {

    static final String GATEWAY = System.getenv().getOrDefault("GATEWAY", "http://localhost:1984"); // ArLocal

    // 10 AR = 10 * 1_000_000_000_000 Winston
    static final long MINT_AMOUNT_WINSTON = 10L * 1_000_000_000_000L; 

    public static void main(String[] args) throws Exception {
        String walletFile = args.length > 0 ? args[0] : "wallet.json";

        ObjectMapper mapper = new ObjectMapper();
        JsonNode jwk = mapper.readTree(Paths.get(walletFile).toFile());
        String address = deriveAddress(jwk.get("n").asText());

        System.out.println("🌐 ArLocal gateway  : " + GATEWAY);
        System.out.println("📬 Wallet address   : " + address);
        System.out.printf ("💰 Minting          : %d Winston (%.0f AR)%n",
                MINT_AMOUNT_WINSTON, MINT_AMOUNT_WINSTON / 1_000_000_000_000.0);

        // ArLocal mint endpoint: GET /mint/{address}/{amount}
        URI uri = URI.create(GATEWAY + "/mint/" + address + "/" + MINT_AMOUNT_WINSTON);
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder().uri(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.err.println("❌ Mint failed. HTTP " + response.statusCode() + ": " + response.body());
            System.exit(1);
        }
        System.out.println("✅ Mint successful!");

        // Confirm new balance
        URI balUri = URI.create(GATEWAY + "/wallet/" + address + "/balance");
        try (var in = balUri.toURL().openStream()) {
            BigInteger winston = new BigInteger(new String(in.readAllBytes()).trim());
            BigDecimal ar = new BigDecimal(winston).divide(BigDecimal.valueOf(1_000_000_000_000L));
            System.out.println();
            System.out.println("💎 New balance      : " + ar + " AR  (" + winston + " Winston)");
        }
    }

    static String deriveAddress(String nFieldFromJwk) throws Exception {
        byte[] nBytes = Base64.getUrlDecoder().decode(nFieldFromJwk);
        byte[] hash   = MessageDigest.getInstance("SHA-256").digest(nBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}