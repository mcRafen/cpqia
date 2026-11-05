package it.cpqia.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

/** "Sign in with Apple" richiede come client secret un JWT ES256 firmato con la chiave .p8 del developer account. */
public class AppleClientSecretGenerator {

    private final String clientId;
    private final String teamId;
    private final String keyId;
    private final ECPrivateKey privateKey;

    public AppleClientSecretGenerator(String clientId, String teamId, String keyId, String privateKeyFile) {
        this.clientId = clientId;
        this.teamId = teamId;
        this.keyId = keyId;
        try {
            String pem = Files.readString(Path.of(privateKeyFile))
                    .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            this.privateKey = (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Chiave privata Apple (.p8) non leggibile: " + privateKeyFile, e);
        }
    }

    public String generate() {
        try {
            Date now = new Date();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(teamId)
                    .issueTime(now)
                    .expirationTime(new Date(now.getTime() + 10 * 60 * 1000L))
                    .audience("https://appleid.apple.com")
                    .subject(clientId)
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(), claims);
            jwt.sign(new ECDSASigner(privateKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Impossibile generare il client secret Apple", e);
        }
    }
}
