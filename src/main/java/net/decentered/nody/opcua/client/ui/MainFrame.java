package net.decentered.nody.opcua.client.ui;

import net.decentered.nody.opcua.client.model.ConnectionConfig;
import net.decentered.nody.opcua.client.model.NodeAttribute;
import net.decentered.nody.opcua.client.security.CertificateManager;
import net.decentered.nody.opcua.client.service.OpcUaClientListener;
import net.decentered.nody.opcua.client.service.OpcUaClientService;
import org.eclipse.milo.opcua.sdk.client.nodes.UaNode;

import javax.swing.*;
import java.awt.*;
import java.util.List;
public class MainFrame extends JFrame implements OpcUaClientListener {

    private static final String APP_TITLE = "Nody OpcUa Client";

    private final OpcUaClientService service;
    private final CertificateManager certManager;

    private final ConnectionPanel connectionPanel;
    private final NodeTreePanel nodeTreePanel;
    private final JPanel attributePanel;
    private final JLabel statusBar;

    public MainFrame() {
        super(APP_TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(700, 450));
        setLocationRelativeTo(null);

        certManager = new CertificateManager();
        service     = new OpcUaClientService(this, certManager);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                service.shutdown();
            }
        });

        connectionPanel = new ConnectionPanel(
                (ConnectionConfig cfg) -> service.connect(cfg),
                ()                     -> service.disconnect()
        );

        nodeTreePanel = new NodeTreePanel(
                parentNodeId -> service.browseNode(parentNodeId),
                nodeId -> { /* TODO handle selected node */ }
        );

        statusBar = new JLabel("Ready");
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));

        attributePanel = new JPanel();

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

        // Help menu
        JMenu helpMenu = new JMenu("Help");

        JMenuItem certInfo = new JMenuItem("Certificate Info…");
        certInfo.setToolTipText("View, export or regenerate the client application certificate");
        certInfo.addActionListener(e ->
                new CertificateInfoDialog(this, certManager).setVisible(true));

        helpMenu.add(certInfo);
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
            //FIXME
            //attributePanel.clear();
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
    // FIXME
        //        SwingUtilities.invokeLater(() -> {
//            attributePanel.showAttributes(nodeId, attributes);
//            setStatus("Attributes loaded for " + nodeId);
//        });
    }

    // -------------------------------------------------------------------------

    private boolean isRootBrowse(String parentNodeId) {
        return parentNodeId == null || parentNodeId.contains("i=84");
    }

    private void setStatus(String text) {
        statusBar.setText(" " + text);
    }
}