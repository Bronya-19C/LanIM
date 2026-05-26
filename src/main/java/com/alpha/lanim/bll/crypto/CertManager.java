package com.alpha.lanim.bll.crypto;

import com.alpha.lanim.util.Constants;
import com.alpha.lanim.util.HashUtil;
import com.alpha.lanim.util.JsonUtil;
import com.google.gson.reflect.TypeToken;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CertManager {

    private static final String KEYSTORE_PATH = Constants.DEFAULT_KEYSTORE_PATH;
    private static final String TRUSTED_PATH = Constants.TRUSTED_PEERS_PATH;
    private static final String KEYSTORE_TYPE = "JKS";
    private static final String ALIAS = "lanim-self";
    private static final char[] PASSWORD = "lanim-local-dev".toCharArray();
    private static final int KEY_SIZE = 2048;
    private static final int VALIDITY_DAYS = 365;

    private final Map<String, String> trustedFingerprints;
    private KeyStore keyStore;
    private boolean initialized;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public CertManager() {
        this.trustedFingerprints = new HashMap<>();
    }

    public synchronized void init() throws Exception {
        if (initialized) return;

        Path keyStoreFile = Paths.get(KEYSTORE_PATH);
        Path parent = keyStoreFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        keyStore = KeyStore.getInstance(KEYSTORE_TYPE);

        if (Files.exists(keyStoreFile)) {
            try (FileInputStream fis = new FileInputStream(keyStoreFile.toFile())) {
                keyStore.load(fis, PASSWORD);
            }
        } else {
            keyStore.load(null, PASSWORD);
            generateSelfSignedCert();
            saveKeyStore();
        }

        loadTrustedPeers();
        initialized = true;
    }

    private void generateSelfSignedCert() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(KEY_SIZE);
        KeyPair keyPair = kpg.generateKeyPair();

        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + VALIDITY_DAYS * 86400000L);

        X500Name subject = new X500Name("CN=LANIM-Peer, O=LANIM, L=Local");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, keyPair.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .build(keyPair.getPrivate());

        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter()
                .getCertificate(certHolder);

        keyStore.setKeyEntry(ALIAS, keyPair.getPrivate(), PASSWORD,
                new java.security.cert.Certificate[]{cert});
    }

    private void saveKeyStore() throws Exception {
        Path keyStoreFile = Paths.get(KEYSTORE_PATH);
        try (FileOutputStream fos = new FileOutputStream(keyStoreFile.toFile())) {
            keyStore.store(fos, PASSWORD);
        }
    }

    private void loadTrustedPeers() {
        Path path = Paths.get(TRUSTED_PATH);
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> loaded = JsonUtil.gson().fromJson(json, type);
                if (loaded != null) {
                    trustedFingerprints.putAll(loaded);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public synchronized void saveTrustedPeers() throws Exception {
        Path path = Paths.get(TRUSTED_PATH);
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        String json = JsonUtil.toJson(trustedFingerprints);
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    public SSLContext createServerSSLContext() throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, PASSWORD);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    public SSLContext createClientSSLContext() throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{new TofuTrustManager()}, null);
        return ctx;
    }

    public static String getCertFingerprint(java.security.cert.Certificate cert) {
        try {
            return HashUtil.sha256Hex(cert.getEncoded());
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, String> getTrustedFingerprints() {
        return new HashMap<>(trustedFingerprints);
    }

    public boolean isTrusted(String addressKey) {
        return trustedFingerprints.containsKey(addressKey);
    }

    public void removeTrusted(String addressKey) {
        trustedFingerprints.remove(addressKey);
    }

    private class TofuTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (chain == null || chain.length == 0) {
                throw new CertificateException("No certificate provided");
            }

            X509Certificate cert = chain[0];
            try {
                cert.checkValidity();
            } catch (Exception e) {
                throw new CertificateException("Certificate is not valid: " + e.getMessage());
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    public void trustPeer(String addressKey, String fingerprint) throws Exception {
        trustedFingerprints.put(addressKey, fingerprint);
        saveTrustedPeers();
    }

    public boolean verifyPeerFingerprint(String addressKey, String fingerprint) {
        String stored = trustedFingerprints.get(addressKey);
        if (stored == null) {
            return true;
        }
        return stored.equals(fingerprint);
    }
}
