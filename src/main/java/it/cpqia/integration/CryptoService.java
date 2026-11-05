package it.cpqia.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Cifratura AES-GCM dei segreti salvati nel database (es. password IBM i). Chiave da cpqia.crypto-key. */
@Service
public class CryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);
    private final SecretKey key;
    private final boolean customKey;

    public CryptoService(@Value("${cpqia.crypto-key:}") String secret) {
        this.customKey = secret != null && !secret.isBlank();
        if (!customKey) {
            log.warn("cpqia.crypto-key non impostata: le password salvate sono cifrate con una chiave predefinita (poco sicura). Impostala nella configurazione.");
        }
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest((customKey ? secret : "cpqia-default-key-change-me").getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(h, "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean customKey() {
        return customKey;
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] enc = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(enc, 0, all, iv.length, enc.length);
            return Base64.getEncoder().encodeToString(all);
        } catch (Exception e) {
            throw new IllegalStateException("Cifratura non riuscita", e);
        }
    }

    public String decrypt(String token) {
        try {
            byte[] all = Base64.getDecoder().decode(token);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Password non decifrabile: la chiave cpqia.crypto-key è cambiata? Reinseriscila.", e);
        }
    }
}
