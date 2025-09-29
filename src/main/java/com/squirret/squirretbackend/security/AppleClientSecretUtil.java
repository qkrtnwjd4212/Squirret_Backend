package com.squirret.squirretbackend.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

public class AppleClientSecretUtil {

    /**
     * @param teamId   Apple Developer Team ID
     * @param clientId Apple Service ID (웹용)
     * @param keyId    Key ID
     * @param p8Pem    -----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----
     */
    public static String create(String teamId, String clientId, String keyId, String p8Pem) {
        try {
            ECPrivateKey privateKey = readECPrivateKey(p8Pem);

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .type(JOSEObjectType.JWT)
                    .keyID(keyId)
                    .build();

            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(teamId)
                    .subject(clientId)
                    .audience("https://appleid.apple.com")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(180, ChronoUnit.DAYS)))
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new ECDSASigner(privateKey));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Apple client_secret", e);
        }
    }

    private static ECPrivateKey readECPrivateKey(String pem) throws Exception {
        String content = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(content);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(spec);
    }
}
