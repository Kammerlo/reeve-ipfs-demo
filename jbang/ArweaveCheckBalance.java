///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Base64;

public class ArweaveCheckBalance {

    static final String GATEWAY = System.getenv().getOrDefault("GATEWAY", "http://localhost:1984"); // ArLocal

    public static void main(String[] args) throws Exception {
        String walletFile = args.length > 0 ? args[0] : "wallet.json";

        ObjectMapper mapper = new ObjectMapper();
        JsonNode jwk = mapper.readTree(Paths.get(walletFile).toFile());
        String address = deriveAddress(jwk.get("n").asText());

        System.out.println("🌐 ArLocal gateway  : " + GATEWAY);
        System.out.println("📬 Wallet address   : " + address);

        URI uri = URI.create(GATEWAY + "/wallet/" + address + "/balance");
        System.out.println("🔍 Querying         : " + uri);

        try (var in = uri.toURL().openStream()) {
            String body = new String(in.readAllBytes()).trim();
            BigInteger winston = new BigInteger(body);
            BigDecimal ar = new BigDecimal(winston)
                    .divide(BigDecimal.valueOf(1_000_000_000_000L));

            System.out.println();
            System.out.println("💰 Balance (Winston) : " + winston);
            System.out.println("💎 Balance (AR)      : " + ar + " AR");

            if (winston.compareTo(BigInteger.ZERO) == 0) {
                System.out.println();
                System.out.println("⚠️  Balance is 0. Run: jbang ArweaveFundWallet.java");
            }
        }
    }

    static String deriveAddress(String nFieldFromJwk) throws Exception {
        byte[] nBytes = Base64.getUrlDecoder().decode(nFieldFromJwk);
        byte[] hash   = MessageDigest.getInstance("SHA-256").digest(nBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}