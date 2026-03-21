package net.decentered.nody.opcua.client.security;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
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
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

/**
 * Creates and exports self-signed <em>user identity</em> certificates for
 * OPC UA X.509 user authentication (OPC UA Part 4 §5.6.3.3).
 *
 * <h3>User cert vs. application cert</h3>
 * <ul>
 *   <li>The <b>application certificate</b> ({@link CertificateManager}) secures the
 *       transport channel and carries a {@code SubjectAlternativeName} with the
 *       OPC UA application URI.</li>
 *   <li>The <b>user certificate</b> (this class) identifies a <em>human user</em>
 *       to the server.  It does <em>not</em> carry an application URI SAN.
 *       The server verifies that the client signed the session nonce with the
 *       corresponding private key.</li>
 * </ul>
 *
 * <h3>Workflow</h3>
 * <ol>
 *   <li>Call {@link #generate(String, String, String, int, Path, char[])} to create
 *       a key pair + self-signed cert and save both to a PKCS#12 keystore.</li>
 *   <li>Call {@link #exportPublicKeyPem(Path)} to write the certificate as PEM
 *       so the server administrator can add it to the server's user trust list.</li>
 * </ol>
 */
public class UserCertificateManager {

    private static final Logger LOG = LoggerFactory.getLogger(UserCertificateManager.class);

    private static final int KEY_BITS     = 2048;
    private static final String KS_TYPE   = "PKCS12";
    private static final String ALIAS     = "user-identity";
    private static final String SIGN_ALG  = "SHA256withRSA";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates a self-signed user identity certificate and saves it to a
     * PKCS#12 keystore at {@code outputPath}.
     *
     * @param commonName   the user's display name (CN field, e.g. "John Doe")
     * @param organisation organisation name (O field, may be empty)
     * @param country      two-letter country code (C field, e.g. "DE")
     * @param validityYears certificate validity in years (1–30)
     * @param outputPath   destination path for the PKCS#12 file (*.p12)
     * @param password     keystore password – the array is zeroed after use
     * @return the generated certificate (for display / export)
     * @throws Exception on any crypto or I/O error
     */
    public X509Certificate generate(String commonName,
                                    String organisation,
                                    String country,
                                    int    validityYears,
                                    Path   outputPath,
                                    char[] password) throws Exception {
        LOG.info("Generating user certificate: CN={}, O={}, C={}, years={}",
                commonName, organisation, country, validityYears);

        // ── Key pair ──────────────────────────────────────────────────────────
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                "RSA", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(KEY_BITS, new SecureRandom());
        KeyPair keyPair = kpg.generateKeyPair();

        // ── DN ────────────────────────────────────────────────────────────────
        String dnStr = buildDn(commonName, organisation, country);
        X500Name dn  = new X500Name(dnStr);

        // ── Validity ──────────────────────────────────────────────────────────
        Instant now    = Instant.now();
        Instant expiry = now.plus((long) validityYears * 365, ChronoUnit.DAYS);

        // ── Certificate builder ───────────────────────────────────────────────
        var builder = new JcaX509v3CertificateBuilder(
                dn,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now),
                Date.from(expiry),
                dn,
                keyPair.getPublic());

        // KeyUsage: digitalSignature only (used to sign the server nonce)
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));

        // ExtendedKeyUsage: clientAuth (OPC UA user identity)
        builder.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));

        // BasicConstraints: not a CA
        builder.addExtension(Extension.basicConstraints, true,
                new BasicConstraints(false));

        // SubjectKeyIdentifier (informational)
        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(
                keyPair.getPublic().getEncoded());
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                new SubjectKeyIdentifier(spki.getEncoded()));

        // ── Sign ──────────────────────────────────────────────────────────────
        ContentSigner signer = new JcaContentSignerBuilder(SIGN_ALG)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());

        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));

        // ── Save PKCS#12 ──────────────────────────────────────────────────────
        Files.createDirectories(outputPath.getParent() == null
                ? outputPath.toAbsolutePath().getParent() : outputPath.getParent());
        KeyStore ks = KeyStore.getInstance(KS_TYPE);
        ks.load(null, null);
        ks.setKeyEntry(ALIAS, keyPair.getPrivate(), password,
                new java.security.cert.Certificate[]{certificate});
        try (var out = Files.newOutputStream(outputPath)) {
            ks.store(out, password);
        }
        // Zero the keystore password immediately after saving
        Arrays.fill(password, '\0');

        LOG.info("User certificate saved to {}", outputPath);
        return certificate;
    }

    /**
     * Exports a certificate stored in a PKCS#12 file as PEM, writing the
     * result to {@code pemPath} (e.g. {@code user-identity.pem}).
     *
     * @param p12Path  source PKCS#12 keystore
     * @param p12Pass  keystore password (zeroed after use)
     * @param pemPath  destination PEM file
     * @return the PEM string (also written to {@code pemPath})
     */
    public String exportPublicKeyPem(Path p12Path, char[] p12Pass, Path pemPath)
            throws Exception {
        KeyStore ks = KeyStore.getInstance(KS_TYPE);
        try (var in = Files.newInputStream(p12Path)) {
            ks.load(in, p12Pass);
        }
        Arrays.fill(p12Pass, '\0');

        String alias = ks.aliases().nextElement();
        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
        String pem = toPem(cert);
        Files.writeString(pemPath, pem);
        LOG.info("User certificate PEM exported to {}", pemPath);
        return pem;
    }

    /**
     * Reads and returns the {@link X509Certificate} from a PKCS#12 file
     * without touching the private key (for display purposes only).
     */
    public X509Certificate readCertificate(Path p12Path, char[] p12Pass) throws Exception {
        KeyStore ks = KeyStore.getInstance(KS_TYPE);
        try (var in = Files.newInputStream(p12Path)) {
            ks.load(in, p12Pass);
        }
        Arrays.fill(p12Pass, '\0');
        String alias = ks.aliases().nextElement();
        return (X509Certificate) ks.getCertificate(alias);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String buildDn(String cn, String o, String c) {
        StringBuilder sb = new StringBuilder("CN=").append(escape(cn));
        if (!o.isBlank()) sb.append(", O=").append(escape(o));
        if (!c.isBlank()) sb.append(", C=").append(escape(c));
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace(",", "\\,").replace("=", "\\=");
    }

    public static String toPem(X509Certificate cert) throws Exception {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(cert.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + b64 + "\n-----END CERTIFICATE-----\n";
    }
}