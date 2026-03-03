///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.util.Base64;

public class ArweaveCreateWallet {

    static final String GATEWAY = System.getenv().getOrDefault("GATEWAY", "http://localhost:1984"); // ArLocal

    public static void main(String[] args) throws Exception {
        System.out.println("🔑 Generating Arweave wallet (RSA-4096)...");

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(4096);
        KeyPair keyPair = kpg.generateKeyPair();

        RSAPrivateCrtKey priv = (RSAPrivateCrtKey) keyPair.getPrivate();
        RSAPublicKey     pub  = (RSAPublicKey)     keyPair.getPublic();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jwk = mapper.createObjectNode();
        jwk.put("kty", "RSA");
        jwk.put("ext", true);
        jwk.put("e",   bnToBase64Url(pub.getPublicExponent()));
        jwk.put("n",   bnToBase64Url(pub.getModulus()));
        jwk.put("d",   bnToBase64Url(priv.getPrivateExponent()));
        jwk.put("p",   bnToBase64Url(priv.getPrimeP()));
        jwk.put("q",   bnToBase64Url(priv.getPrimeQ()));
        jwk.put("dp",  bnToBase64Url(priv.getPrimeExponentP()));
        jwk.put("dq",  bnToBase64Url(priv.getPrimeExponentQ()));
        jwk.put("qi",  bnToBase64Url(priv.getCrtCoefficient()));

        String address = deriveAddress(jwk.get("n").asText());

        if (address.length() != 43) {
            throw new IllegalStateException(
                "Address length is " + address.length() + ", expected 43!");
        }

        String walletFile = "wallet.json";
        mapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(walletFile).toFile(), jwk);

        System.out.println("✅ Wallet created!");
        System.out.println("🌐 ArLocal gateway  : " + GATEWAY);
        System.out.println("📁 Saved to         : " + walletFile);
        System.out.println("📬 Your address     : " + address);
        System.out.println();
        System.out.println("⚠️  Keep wallet.json secret — it contains your private key!");
        System.out.println();
        System.out.println("👉 Next: fund your wallet with:");
        System.out.println("   jbang ArweaveFundWallet.java");
    }

    /**
     * Mirrors arweave-js bn2base64url():
     *   BigInteger → hex string (even-length padded) → raw bytes → Base64Url
     */
    static String bnToBase64Url(BigInteger bn) {
        String hex = bn.toString(16);
        if (hex.length() % 2 != 0) hex = "0" + hex;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hexToBytes(hex));
    }

    static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            data[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        return data;
    }

    /**
     * Mirrors arweave-js ownerToAddress():
     *   address = Base64Url( SHA-256( Base64UrlDecode(n) ) )
     */
    static String deriveAddress(String nFieldFromJwk) throws Exception {
        byte[] nBytes = Base64.getUrlDecoder().decode(nFieldFromJwk);
        byte[] hash   = MessageDigest.getInstance("SHA-256").digest(nBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}