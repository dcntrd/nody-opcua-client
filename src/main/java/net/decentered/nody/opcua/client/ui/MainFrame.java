package net.decentered.nody.opcua.client.ui;

import net.decentered.nody.opcua.client.model.ConnectionConfig;
import net.decentered.nody.opcua.client.model.NodeAttribute;
import net.decentered.nody.opcua.client.security.CertificateManager;
import net.decentered.nody.opcua.client.service.OpcUaClientListener;
import net.decentered.nody.opcua.client.service.OpcUaClientService;
import net.decentered.nody.opcua.client.settings.ConnectionSettings;
import org.eclipse.milo.opcua.sdk.client.nodes.UaNode;

import javax.swing.*;
import java.awt.*;
import java.util.List;
public class MainFrame extends JFrame implements OpcUaClientListener {

    private static final String APP_TITLE = "Nody OpcUa Client";

    private final OpcUaClientService service;
    private final CertificateManager certManager;
    private final ConnectionSettings connectionSettings;

    private final ConnectionPanel connectionPanel;
    private final NodeTreePanel nodeTreePanel;
    private final AttributePanel attributePanel;
    private final JLabel statusBar;

    public MainFrame() {
        super(APP_TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(700, 450));
        setLocationRelativeTo(null);

        certManager = new CertificateManager();
        connectionSettings = new ConnectionSettings();
        service     = new OpcUaClientService(this, certManager);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                service.shutdown();
            }
        });

        connectionPanel = new ConnectionPanel(
                connectionSettings,
                (ConnectionConfig cfg) -> service.connect(cfg),
                ()                     -> service.disconnect()
        );

        attributePanel = new AttributePanel();

        nodeTreePanel = new NodeTreePanel(
                parentNodeId -> service.browseNode(parentNodeId),
                nodeId -> {
                    attributePanel.showLoading(nodeId.toParseableString());
                    service.readAttributes(nodeId);
                }
        );

        statusBar = new JLabel("Ready");
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, nodeTreePanel, attributePanel);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.35);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(connectionPanel, BorderLayout.NORTH);
        getContentPane().add(splitPane,       BorderLayout.CENTER);
        getContentPane().add(statusBar,       BorderLayout.SOUTH);

        setJMenuBar(buildMenuBar());
    }


    // =========================================================================
    // Menu bar
    // =========================================================================

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

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
            connectionPanel.setConnected(endpointUrl);
            setStatus("Connected to " + endpointUrl + " – browsing root…");
            nodeTreePanel.clear();
            service.browseNode(null);
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            connectionPanel.setDisconnected(reason);
            nodeTreePanel.clear();
            attributePanel.clear();
            setStatus("Disconnected: " + reason);
        });
    }

    @Override
    public void onError(String operation, Throwable cause) {
        SwingUtilities.invokeLater(() -> {
            String msg = operation + " failed: " + cause.getMessage();
            setStatus("Error – " + msg);
            if ("connect".equals(operation)) {
                connectionPanel.setError(msg);
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
            attributePanel.showAttributes(nodeId, attributes);
            setStatus("Attributes loaded for " + nodeId);
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