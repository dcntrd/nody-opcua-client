package net.decentered.nody.opcua.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.identity.IdentityProvider;
import org.eclipse.milo.opcua.stack.core.security.*;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.util.function.Predicate;

public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    private static TrustListManager clientTrustListManager;

    static {
        // Required for SecurityPolicy.Aes256_Sha256_RsaPss
        Security.addProvider(new BouncyCastleProvider());
    }

    static void main() {

        logger.info("Starting Client");

        try {
            //OpcUaClient client = createClient("opc.tcp://milo.digitalpetri.com:62541/milo");
            OpcUaClient client = createClient("opc.tcp://opcuaserver.com:48010");
            client.connect();

            client.disconnect();

        } catch (Exception e) {
            logger.error(e);
        }


    }

    private static OpcUaClient createClient(String url) throws Exception {
        Path securityTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "client", "security");
        Files.createDirectories(securityTempDir);
        if (!Files.exists(securityTempDir)) {
            throw new Exception("unable to create security dir: " + securityTempDir);
        }

        Path pkiDir = securityTempDir.resolve("pki");

        logger.info("security dir: {}", securityTempDir.toAbsolutePath());
        logger.info("security pki dir: {}", pkiDir.toAbsolutePath());

        KeyStoreLoader loader = new KeyStoreLoader().load(securityTempDir);

        clientTrustListManager = FileBasedTrustListManager.createAndInitialize(pkiDir);

        var certificateValidator =
                new DefaultClientCertificateValidator(
                        clientTrustListManager, new MemoryCertificateQuarantine());

        return OpcUaClient.create(
                url,
                endpoints -> endpoints.stream().filter(endpointFilter()).findFirst(),
                transportConfigBuilder -> {},
                clientConfigBuilder ->
                        clientConfigBuilder
                                .setApplicationName(LocalizedText.english("eclipse milo opc-ua client"))
                                .setApplicationUri("urn:eclipse:milo:examples:client")
                                .setKeyPair(loader.getClientKeyPair())
                                .setCertificate(loader.getClientCertificate())
                                .setCertificateChain(loader.getClientCertificateChain())
                                .setCertificateValidator(certificateValidator)
                                .setIdentityProvider(getIdentityProvider()));
    }

    private static Predicate<EndpointDescription> endpointFilter() {
        return e -> getSecurityPolicy().getUri().equals(e.getSecurityPolicyUri());
    }

    private static SecurityPolicy getSecurityPolicy() {
        //return SecurityPolicy.Basic256Sha256;
        return SecurityPolicy.None;
    }

    private static IdentityProvider getIdentityProvider() {
        return new AnonymousProvider();
    }

}
