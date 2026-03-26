package net.decentered.nody.opcua.client.ui;

import net.decentered.nody.opcua.client.model.ConnectionConfig;
import net.decentered.nody.opcua.client.model.NodeAttribute;
import net.decentered.nody.opcua.client.security.CertificateManager;
import net.decentered.nody.opcua.client.service.OpcUaClientListener;
import net.decentered.nody.opcua.client.service.OpcUaClientService;
import net.decentered.nody.opcua.client.settings.ConnectionProfileStore;
import net.decentered.nody.opcua.client.subscription.SubscriptionService;
import org.eclipse.milo.opcua.sdk.client.nodes.UaNode;

import javax.swing.*;
import java.awt.*;
import java.util.List;
public class MainFrame extends JFrame implements OpcUaClientListener {

    private static final String APP_TITLE = "Nody OpcUa Client";

    private final OpcUaClientService service;
    private final CertificateManager certManager;
    private final ConnectionProfileStore profileStore;

    private final QuickConnectBar quickConnectBar;
    private final NodeTreePanel nodeTreePanel;
    private final RightPanel rightPanel;
    private final JLabel statusBar;

    private final SubscriptionService subscriptionService;

    public MainFrame() {
        super(APP_TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(700, 450));
        setLocationRelativeTo(null);

        certManager  = new CertificateManager();
        profileStore = new ConnectionProfileStore();
        service      = new OpcUaClientService(this, certManager);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                service.shutdown();
            }
        });

        quickConnectBar = new QuickConnectBar(
                profileStore,
                cfg -> service.connect(cfg),
                ()  -> service.disconnect());

        subscriptionService = new SubscriptionService();

        rightPanel = new RightPanel();
        rightPanel.setSubscriptionService(subscriptionService);

        subscriptionService.setCallbacks(rightPanel::onDataChange, rightPanel::onEvent, this::onError);

        nodeTreePanel = new NodeTreePanel(
                parentNodeId -> service.browseNode(parentNodeId),
                nodeId -> {
                    rightPanel.showLoading(nodeId.toParseableString());
                    service.readAttributes(nodeId);
                },
                nsIndex -> service.resolveNamespaceUri(nsIndex),
                (nodeId, variant) -> service.writeValue(nodeId, variant));

        statusBar = new JLabel("Ready");
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                nodeTreePanel, rightPanel);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.35);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(quickConnectBar, BorderLayout.NORTH);
        getContentPane().add(splitPane,       BorderLayout.CENTER);
        getContentPane().add(statusBar,       BorderLayout.SOUTH);

        setJMenuBar(buildMenuBar());
    }


    // =========================================================================
    // Menu bar
    // =========================================================================

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        // ── Connections menu ──────────────────────────────────────────────────
        JMenu connectionsMenu = new JMenu("Connections");

        JMenuItem quickItem = new JMenuItem("Quick Connect…");
        quickItem.setToolTipText(
                "Connect to a server without saving a profile first");
        quickItem.addActionListener(e -> {
            AdHocConnectionDialog dlg =
                    new AdHocConnectionDialog(this, profileStore);
            dlg.setVisible(true);  // blocks until closed
            ConnectionConfig cfg = dlg.getResult();
            if (cfg != null) {
                quickConnectBar.reloadProfiles();  // in case user saved a profile
                quickConnectBar.setConnecting();
                service.connect(cfg);
            }
        });

        JMenuItem manageItem = new JMenuItem("Manage Connections…");
        manageItem.setToolTipText("Add, edit and delete saved connection profiles");
        manageItem.addActionListener(e -> {
            new ConnectionManagerDialog(this, profileStore).setVisible(true);
            quickConnectBar.reloadProfiles();
        });

        connectionsMenu.add(quickItem);
        connectionsMenu.addSeparator();
        connectionsMenu.add(manageItem);
        bar.add(connectionsMenu);

        JMenu securityMenu = new JMenu("Security");

        JMenuItem appCertItem = new JMenuItem("Application Certificate Info…");
        appCertItem.setToolTipText(
                "View, export or regenerate the client application certificate " +
                        "used for the secure channel");
        appCertItem.addActionListener(e ->
                new CertificateInfoDialog(this, certManager).setVisible(true));

        JMenuItem userCertItem = new JMenuItem("Create / Export User Certificate…");
        userCertItem.setToolTipText(
                "Generate a self-signed user identity certificate (X.509) " +
                        "for OPC UA user authentication and export its public key as PEM");
        userCertItem.addActionListener(e ->
                new UserCertificateDialog(this).setVisible(true));

        securityMenu.add(appCertItem);
        securityMenu.addSeparator();
        securityMenu.add(userCertItem);
        bar.add(securityMenu);

        JMenu helpMenu = new JMenu("Help");

        JMenuItem aboutItem = new JMenuItem("About…");
        aboutItem.setToolTipText("Show application information and license");
        aboutItem.addActionListener(e -> new AboutDialog(this).setVisible(true));

        helpMenu.add(aboutItem);
        bar.add(helpMenu);

        return bar;
    }

    // =========================================================================
    // OpcUaClientListener – all arrive on worker thread, dispatch to EDT
    // =========================================================================

    @Override
    public void onConnected(String endpointUrl) {
        SwingUtilities.invokeLater(() -> {
            quickConnectBar.setConnected(endpointUrl);
            setStatus("Connected to " + endpointUrl + " – browsing root…");
            subscriptionService.onConnected(service.getClient());
            nodeTreePanel.clear();
            service.browseNode(null);
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            quickConnectBar.setDisconnected(reason);
            subscriptionService.onDisconnected();
            nodeTreePanel.clear();
            rightPanel.clear();
            setStatus("Disconnected: " + reason);
        });
    }

    @Override
    public void onError(String operation, Throwable cause) {
        SwingUtilities.invokeLater(() -> {
            String msg = operation + " failed: " + cause.getMessage();
            setStatus("Error – " + msg);
            if ("connect".equals(operation)) {
                quickConnectBar.setError(msg);
                JOptionPane.showMessageDialog(this, msg, "Connection Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    @Override
    public void onNodesBrowsed(String parentNodeId, List<UaNode> children) {

        SwingUtilities.invokeLater(() -> {
            if (isRootBrowse(parentNodeId)) {
                nodeTreePanel.setRootChildren(children);
                setStatus("Loaded " + children.size() + " root nodes.");
            } else {
                nodeTreePanel.appendChildren(parentNodeId, children);
                setStatus("Loaded " + children.size() + " children for " + parentNodeId);
            }
        });
    }

    @Override
    public void onAttributesRead(String nodeId, List<NodeAttribute> attributes) {
        SwingUtilities.invokeLater(() -> {
            rightPanel.showAttributes(nodeId, attributes);
            setStatus("Attributes loaded for " + nodeId);
        });
    }

    @Override
    public void onWriteComplete(String nodeId) {
        SwingUtilities.invokeLater(() -> {
            setStatus("Write successful: " + nodeId);
            // Re-read the attributes so the panel reflects the new value
            service.readAttributes(
                    org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
                            .parseSafe(nodeId).orElse(null));
        });
    }

    // -------------------------------------------------------------------------

    private boolean isRootBrowse(String parentNodeId) {
        return parentNodeId == null || parentNodeId.contains("i=84");
    }

    private void setStatus(String text) {
        statusBar.setText(" " + text);
    }
}