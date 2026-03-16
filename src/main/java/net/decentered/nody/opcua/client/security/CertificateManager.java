package net.decentered.nody.opcua.client.security;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Manages the Nody OpcUa Client's self-signed application certificate.
 *
 * <h3>Certificate lifecycle</h3>
 * <ol>
 *   <li>On first use {@link #getOrCreate()} generates an RSA-2048 key pair
 *       and a self-signed X.509v3 certificate valid for 10 years.</li>
 *   <li>Both are persisted in a PKCS#12 keystore at
 *       {@code ~/.nody-opcua-client/pki/client.p12}.</li>
 *   <li>On subsequent starts the keystore is reloaded so the server only
 *       needs to trust the certificate once.</li>
 * </ol>
 *
 * <h3>Certificate extensions (OPC UA compliant)</h3>
 * <ul>
 *   <li>SubjectAlternativeName – OPC UA application URI (mandatory by spec)</li>
 *   <li>KeyUsage – digitalSignature | keyEncipherment | dataEncipherment</li>
 *   <li>ExtendedKeyUsage – clientAuth</li>
 *   <li>BasicConstraints – not a CA</li>
 *   <li>SubjectKeyIdentifier – informational</li>
 * </ul>
 *
 * <h3>Trust store directories</h3>
 * <ul>
 *   <li>{@code ~/.nody-opcua-client/pki/trusted/}  – DER certificates that
 *       the client explicitly trusts (e.g. the server's certificate)</li>
 *   <li>{@code ~/.nody-opcua-client/pki/rejected/} – certificates the stack
 *       rejected; move to {@code trusted/} to accept them</li>
 * </ul>
 */
public class CertificateManager {

    private static final Logger LOG = LoggerFactory.getLogger(CertificateManager.class);

    public static final String APPLICATION_URI  = "urn:net.decentered:nody:opcua-client";
    public static final String APPLICATION_NAME = "Nody OpcUa Client";

    private static final String ALIAS         = "nody-opcua-client";
    private static final String KEYSTORE_PASS = "nody-opcua-client";
    private static final String KEYSTORE_FILE = "client.p12";
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final int    KEY_BITS      = 2048;
    private static final int    CERT_YEARS    = 10;

    /** Base PKI directory: {@code ~/.nody-opcua-client/pki} */
    public static final Path PKI_DIR = Paths.get(System.getProperty("user.home"), ".nody-opcua-client", "pki");

    /** Trusted server-certificate directory. */
    public static final Path TRUSTED_DIR  = PKI_DIR.resolve("trusted");

    /** Rejected certificate directory. */
    public static final Path REJECTED_DIR = PKI_DIR.resolve("rejected");

    private final Path keystorePath;

    private KeyPair         keyPair;
    private X509Certificate certificate;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public CertificateManager() {
        this.keystorePath = PKI_DIR.resolve(KEYSTORE_FILE);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the client {@link KeyPair}, loading from the keystore on disk
     * or generating a fresh self-signed certificate if none exists yet.
     *
     * @throws Exception if key generation or keystore I/O fails
     */
    public KeyPair getOrCreate() throws Exception {
        if (keyPair != null) return keyPair;
        ensureDirectories();
        if (Files.exists(keystorePath)) {
            load();
        } else {
            generate();
            save();
        }
        LOG.info("Client certificate ready: subject={}, serial={}, expires={}",
                certificate.getSubjectX500Principal().getName(),
                certificate.getSerialNumber(),
                certificate.getNotAfter());
        return keyPair;
    }

    /**
     * Returns the self-signed client {@link X509Certificate}.
     * {@link #getOrCreate()} must be called first.
     */
    public X509Certificate getCertificate() {
        if (certificate == null) throw new IllegalStateException("Call getOrCreate() first");
        return certificate;
    }

    /** Path to the PKCS#12 keystore file on disk. */
    public Path getKeystorePath() { return keystorePath; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void ensureDirectories() throws Exception {
        Files.createDirectories(TRUSTED_DIR);
        Files.createDirectories(REJECTED_DIR);
    }

    /** Loads an existing keystore from {@link #keystorePath}. */
    private void load() throws Exception {
        LOG.info("Loading existing client certificate from {}", keystorePath);
        KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
        try (var in = Files.newInputStream(keystorePath)) {
            ks.load(in, KEYSTORE_PASS.toCharArray());
        }
        PrivateKey privateKey = (PrivateKey) ks.getKey(ALIAS, KEYSTORE_PASS.toCharArray());
        certificate = (X509Certificate) ks.getCertificate(ALIAS);
        keyPair = new KeyPair(certificate.getPublicKey(), privateKey);
    }

    /** Generates a new RSA-2048 key pair and a self-signed X.509v3 certificate. */
    private void generate() throws Exception {
        LOG.info("Generating new self-signed RSA-{} client certificate …", KEY_BITS);

        // Key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                "RSA", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(KEY_BITS, new SecureRandom());
        keyPair = kpg.generateKeyPair();

        // Validity window
        Instant now    = Instant.now();
        Instant expiry = now.plus(CERT_YEARS * 365L, ChronoUnit.DAYS);

        // Subject / Issuer DN (same – it's self-signed)
        X500Name dn = new X500Name("CN=" + APPLICATION_NAME + ", O=deCentered, OU=OpcUa, C=AT");

        BigInteger serial = BigInteger.valueOf(now.toEpochMilli());

        // Certificate builder
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                dn, serial, Date.from(now), Date.from(expiry), dn, keyPair.getPublic());

        // Extension 1: SubjectAlternativeName – OPC UA application URI (mandatory)
        builder.addExtension(
                Extension.subjectAlternativeName,
                false,
                new GeneralNames(new GeneralName(
                        GeneralName.uniformResourceIdentifier, APPLICATION_URI)));

        // Extension 2: KeyUsage  (OPC UA Part 6 §6.2.2)
        // Required: digitalSignature + nonRepudiation + keyEncipherment + dataEncipherment.
        // Missing nonRepudiation (contentCommitment) causes Bad_CertificateUseNotAllowed.
        builder.addExtension(
                Extension.keyUsage,
                true,
                new KeyUsage(
                        KeyUsage.digitalSignature |
                                KeyUsage.nonRepudiation   |   // = contentCommitment, mandatory per OPC UA spec
                                KeyUsage.keyEncipherment  |
                                KeyUsage.dataEncipherment));

        // Extension 3: ExtendedKeyUsage  (OPC UA Part 6 §6.2.2)
        // OPC UA application certificates must declare BOTH clientAuth AND serverAuth.
        // Servers may reject certificates with only clientAuth (Bad_CertificateUseNotAllowed).
        builder.addExtension(
                Extension.extendedKeyUsage,
                false,
                new ExtendedKeyUsage(new KeyPurposeId[]{
                        KeyPurposeId.id_kp_clientAuth,
                        KeyPurposeId.id_kp_serverAuth
                }));

        // Extension 4: BasicConstraints – not a CA
        builder.addExtension(
                Extension.basicConstraints,
                true,
                new BasicConstraints(false));

        // Extension 5: SubjectKeyIdentifier (informational)
        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(
                keyPair.getPublic().getEncoded());
        builder.addExtension(
                Extension.subjectKeyIdentifier,
                false,
                new SubjectKeyIdentifier(spki.getEncoded()));

        // Sign with SHA256withRSA
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());

        certificate = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));

        LOG.info("Generated certificate: {}", certificate.getSubjectX500Principal());
    }

    /** Saves the key pair + certificate to a PKCS#12 keystore. */
    private void save() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
        ks.load(null, null);
        ks.setKeyEntry(ALIAS,
                keyPair.getPrivate(),
                KEYSTORE_PASS.toCharArray(),
                new java.security.cert.Certificate[]{certificate});
        try (var out = Files.newOutputStream(keystorePath)) {
            ks.store(out, KEYSTORE_PASS.toCharArray());
        }
        LOG.info("Client certificate saved to {}", keystorePath);
    }
}