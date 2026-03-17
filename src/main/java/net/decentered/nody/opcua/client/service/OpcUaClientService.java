package net.decentered.nody.opcua.client.service;

import net.decentered.nody.opcua.client.model.ConnectionConfig;
import net.decentered.nody.opcua.client.model.NodeAttribute;
import net.decentered.nody.opcua.client.security.CertificateManager;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.nodes.UaNode;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.security.*;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Business-logic layer for OPC UA connectivity using Eclipse Milo.
 *
 * <p>For secured connections ({@code SecurityPolicy} != {@code None}) the
 * service delegates to the injected {@link CertificateManager} to obtain
 * (or auto-generate) the self-signed client application certificate, then
 * passes the {@link KeyPair} and {@link X509Certificate} to the Milo config
 * builder via {@code setKeyPair()} / {@code setCertificate()}.</p>
 *
 * <p>For unsecured connections no certificate material is loaded, keeping
 * the fast path lean.</p>
 *
 * <h3>Sharing {@code CertificateManager}</h3>
 * The same {@link CertificateManager} instance should be injected here and
 * into the UI's {@link net.decentered.nody.opcua.client.ui.CertificateInfoDialog}
 * so that certificate generation happens exactly once per application run.
 */
public class OpcUaClientService {

    private static final Logger LOG = LoggerFactory.getLogger(OpcUaClientService.class);

    private final OpcUaClientListener listener;
    private final CertificateManager  certManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "opcua-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile OpcUaClient client;

    /**
     * @param listener    receives async event callbacks
     * @param certManager shared certificate manager (may be called concurrently
     *                    if the UI dialog is open; {@code getOrCreate()} is safe
     *                    to call multiple times)
     */
    public OpcUaClientService(OpcUaClientListener listener, CertificateManager certManager) {
        this.listener    = listener;
        this.certManager = certManager;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Connects asynchronously using the parameters in {@code config}.
     *
     * <p>When the security policy is not {@code None}, {@link CertificateManager}
     * is used to load or auto-generate the self-signed client certificate before
     * the network connection is attempted, so any I/O error is surfaced early.</p>
     */
    public void connect(ConnectionConfig config) {
        executor.submit(() -> {
            try {
                LOG.info("Connecting to {}  policy={}  mode={}",
                        config.endpointUrl(),
                        config.securityPolicy().name(),
                        config.securityMode());

                boolean secured = config.securityPolicy() != SecurityPolicy.None;

                KeyPair         keyPair     = null;
                X509Certificate certificate = null;

                if (secured) {
                    keyPair     = certManager.getOrCreate();
                    certificate = certManager.getCertificate();
                    LOG.info("Using client certificate: {}",
                            certificate.getSubjectX500Principal().getName());
                }

                // Captured as effectively-final for lambdas
                final KeyPair         fKeyPair = keyPair;
                final X509Certificate fCert    = certificate;

                String endpointUrl = config.endpointUrl();

                OpcUaClient newClient = OpcUaClient.create(

                        config.endpointUrl(),

                        // Match both security policy URI and security mode
                        endpoints -> {
                            String              wantedUri  = config.securityPolicy().getUri();
                            MessageSecurityMode wantedMode = config.securityMode();
                            return endpoints.stream()
                                    .filter(e -> e.getSecurityPolicyUri().equals(wantedUri)
                                            && e.getSecurityMode() == wantedMode)
                                    .findFirst();
                        },

                        transportConfigBuilder -> {},

                        configBuilder -> {
                            configBuilder
                                    .setApplicationName(LocalizedText.english(CertificateManager.APPLICATION_NAME))
                                    .setApplicationUri(CertificateManager.APPLICATION_URI)
                                    .setProductUri("nody-opcua-client")
                                    .setIdentityProvider(getIdentityProvider());

                            if (secured) {
                                configBuilder
                                        .setKeyPair(fKeyPair)
                                        .setCertificate(fCert)
                                        .setCertificateChain(List.of(fCert).toArray(new X509Certificate[0]));
                            }
                        }
                );

                newClient.connect();
                client = newClient;
                LOG.info("Connected to {}", config.endpointUrl());
                listener.onConnected(config.endpointUrl());

            } catch (Exception e) {
                LOG.error("Connect failed", e);
                listener.onError("connect", e);
            }
        });
    }

    private IdentityProvider getIdentityProvider() {
        return new AnonymousProvider();
    }

    /** Disconnects the current session asynchronously. */
    public void disconnect() {
        executor.submit(() -> {
            OpcUaClient c = client;
            client = null;
            if (c != null) {
                try {
                    c.disconnect();
                    listener.onDisconnected("User requested disconnect");
                } catch (Exception e) {
                    LOG.warn("Disconnect error", e);
                    listener.onDisconnected("Disconnect error: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Browses the direct children of {@code parentNodeId} asynchronously.
     *
     * @param parentNodeId NodeId of the parent, or {@code null} for the
     *                     Objects folder (ns=0;i=85).
     */
    public void browseNode(NodeId parentNodeId) {
        executor.submit(() -> {
            OpcUaClient c = client;
            if (c == null) {
                listener.onError("browse", new IllegalStateException("Not connected"));
                return;
            }
            try {
                NodeId nodeId = (parentNodeId != null) ? parentNodeId : NodeIds.RootFolder;
                List<? extends UaNode> nodes = c.getAddressSpace().browseNodes(nodeId);
                listener.onNodesBrowsed(nodeId.toParseableString(), List.copyOf(nodes));
            } catch (Exception e) {
                LOG.error("Browse failed", e);
                listener.onError("browse", e);
            }
        });
    }

    /** Reads the key attributes of {@code nodeId} asynchronously. */
    public void readAttributes(NodeId nodeId) {
        executor.submit(() -> {
            OpcUaClient c = client;
            if (c == null) {
                listener.onError("readAttributes", new IllegalStateException("Not connected"));
                return;
            }
            try {
                UaNode node = c.getAddressSpace().getNode(nodeId);

                List<NodeAttribute> attrs = new ArrayList<>();
                attrs.add(new NodeAttribute("NodeId", nodeId.toParseableString()));

                safeAdd(attrs, "NodeClass",   () -> node.readNodeClass().toString());
                safeAdd(attrs, "BrowseName",  () -> node.readBrowseName().toParseableString());
                safeAdd(attrs, "DisplayName", () -> node.readDisplayName().getText());
                safeAdd(attrs, "Description", () -> {
                    LocalizedText d = node.readDescription();
                    return (d != null && d.getText() != null) ? d.getText() : "<none>";
                });

                for (AttributeId attrId : new AttributeId[]{
                        AttributeId.Value,
                        AttributeId.DataType,
                        AttributeId.ValueRank,
                        AttributeId.AccessLevel,
                        AttributeId.Historizing}) {
                    safeAdd(attrs, attrId.name(), () -> {
                        var dv = node.readAttribute(attrId);
                        var v  = dv.getValue().getValue();
                        return v != null ? v.toString() : "<not applicable>";
                    });
                }

                listener.onAttributesRead(nodeId.toParseableString(), attrs);

            } catch (Exception e) {
                LOG.error("Read attributes failed", e);
                listener.onError("readAttributes", e);
            }
        });
    }

    /** Shuts down the background executor – call on application exit. */
    public void shutdown() {
        executor.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void safeAdd(List<NodeAttribute> list, String name,
                                AttributeSupplier supplier) {
        String value;
        try {
            value = supplier.get();
        } catch (Exception e) {
            value = "<error: " + e.getMessage() + ">";
        }
        list.add(new NodeAttribute(name, value));
    }

    @FunctionalInterface
    private interface AttributeSupplier {
        String get() throws Exception;
    }
}