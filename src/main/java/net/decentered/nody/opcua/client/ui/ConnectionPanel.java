package net.decentered.nody.opcua.client.ui;

import net.decentered.nody.opcua.client.model.ConnectionConfig;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Top panel: OPC UA endpoint URL, security policy combo, security mode combo,
 * and Connect / Disconnect button.
 *
 * <p>The security mode combo is kept in sync with the chosen policy:
 * <ul>
 *   <li>{@code None} policy -> only {@code None} mode is selectable.</li>
 *   <li>Any other policy -> {@code Sign} and {@code SignAndEncrypt} are
 *       available; {@code None} mode is hidden.</li>
 * </ul>
 *
 * Communicates back to {@code MainFrame} exclusively via the
 * {@code onConnect} / {@code onDisconnect} lambdas – no direct dependency
 * on any other class.
 */
public class ConnectionPanel extends JPanel {

    // Security policies offered in the UI (in display order)
    private static final SecurityPolicy[] POLICIES = {
            SecurityPolicy.None,
            SecurityPolicy.Basic128Rsa15,
            SecurityPolicy.Basic256,
            SecurityPolicy.Basic256Sha256,
            SecurityPolicy.Aes128_Sha256_RsaOaep,
            SecurityPolicy.Aes256_Sha256_RsaPss
    };

    private static final MessageSecurityMode[] MODES_NONE  = {
            MessageSecurityMode.None
    };
    private static final MessageSecurityMode[] MODES_SECURE = {
            MessageSecurityMode.Sign,
            MessageSecurityMode.SignAndEncrypt
    };

    // widgets
    private final JTextField                     urlField;
    private final JComboBox<SecurityPolicy>      policyCombo;
    private final JComboBox<MessageSecurityMode> modeCombo;
    private final JButton                        connectButton;
    private final JLabel                         statusLabel;

    // callbacks
    private final Consumer<ConnectionConfig> onConnect;
    private final Runnable                   onDisconnect;

    private boolean connected = false;

    // construction
    public ConnectionPanel(Consumer<ConnectionConfig> onConnect, Runnable onDisconnect) {
        this.onConnect    = onConnect;
        this.onDisconnect = onDisconnect;

        setLayout(new BorderLayout(8, 4));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        // row 1: URL
        JPanel urlRow = new JPanel(new BorderLayout(6, 0));
        urlRow.add(labelOf("OPC UA URL:"), BorderLayout.WEST);

        urlField = new JTextField("opc.tcp://localhost:4840", 50);
        urlField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        urlRow.add(urlField, BorderLayout.CENTER);

        // row 2: security options + button
        JPanel secRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

        secRow.add(labelOf("Security Policy:"));
        policyCombo = new JComboBox<>(POLICIES);
        policyCombo.setRenderer(new PolicyRenderer());
        policyCombo.setSelectedItem(SecurityPolicy.None);
        secRow.add(policyCombo);

        secRow.add(Box.createHorizontalStrut(10));
        secRow.add(labelOf("Message Security Mode:"));
        modeCombo = new JComboBox<>(MODES_NONE);
        modeCombo.setRenderer(new ModeRenderer());
        secRow.add(modeCombo);

        secRow.add(Box.createHorizontalStrut(16));
        connectButton = new JButton("Connect");
        connectButton.setPreferredSize(new Dimension(110, 28));
        secRow.add(connectButton);

        statusLabel = new JLabel("Disconnected");
        statusLabel.setForeground(Color.GRAY);
        secRow.add(statusLabel);

        // assemble
        JPanel rows = new JPanel(new GridLayout(2, 1, 0, 4));
        rows.add(urlRow);
        rows.add(secRow);
        add(rows, BorderLayout.CENTER);

        // wiring
        policyCombo.addActionListener(e -> syncModeCombo());
        connectButton.addActionListener(e -> handleButtonClick());
    }

    // private helpers

    /** Keep mode options in sync with the chosen policy. */
    private void syncModeCombo() {
        SecurityPolicy policy = selectedPolicy();
        MessageSecurityMode currentMode = (MessageSecurityMode) modeCombo.getSelectedItem();

        modeCombo.removeAllItems();
        MessageSecurityMode[] modes = (policy == SecurityPolicy.None) ? MODES_NONE : MODES_SECURE;
        for (MessageSecurityMode m : modes) modeCombo.addItem(m);

        // Restore previous selection if still valid, otherwise pick first
        boolean restored = false;
        for (MessageSecurityMode m : modes) {
            if (m == currentMode) { modeCombo.setSelectedItem(m); restored = true; break; }
        }
        if (!restored) modeCombo.setSelectedIndex(0);
    }

    private void handleButtonClick() {
        if (!connected) {
            String url = urlField.getText().trim();
            if (url.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter an OPC UA endpoint URL.",
                        "Missing URL", JOptionPane.WARNING_MESSAGE);
                return;
            }
            ConnectionConfig cfg = new ConnectionConfig(url, selectedPolicy(), selectedMode());
            if (!cfg.isValid()) {
                JOptionPane.showMessageDialog(this,
                        "Invalid combination: policy '" + cfg.securityPolicy().name() +
                                "' cannot be used with mode '" + cfg.securityMode() + "'.",
                        "Invalid Security Settings", JOptionPane.WARNING_MESSAGE);
                return;
            }
            setConnecting();
            onConnect.accept(cfg);
        } else {
            setDisconnecting();
            onDisconnect.run();
        }
    }

    private SecurityPolicy      selectedPolicy() { return (SecurityPolicy)      policyCombo.getSelectedItem(); }
    private MessageSecurityMode selectedMode()   { return (MessageSecurityMode) modeCombo.getSelectedItem(); }

    private static JLabel labelOf(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.PLAIN));
        return l;
    }

    // public state methods (called from EDT by MainFrame)

    public void setConnecting() {
        connectButton.setEnabled(false);
        connectButton.setText("Connecting…");
        urlField.setEnabled(false);
        policyCombo.setEnabled(false);
        modeCombo.setEnabled(false);
        statusLabel.setText("Connecting…");
        statusLabel.setForeground(Color.ORANGE.darker());
    }

    public void setConnected(String url) {
        connected = true;
        connectButton.setEnabled(true);
        connectButton.setText("Disconnect");
        statusLabel.setText("Connected  ●");
        statusLabel.setForeground(new Color(0, 150, 0));
    }

    public void setDisconnecting() {
        connectButton.setEnabled(false);
        connectButton.setText("Disconnecting…");
        statusLabel.setText("Disconnecting…");
        statusLabel.setForeground(Color.ORANGE.darker());
    }

    public void setDisconnected(String reason) {
        connected = false;
        connectButton.setEnabled(true);
        connectButton.setText("Connect");
        urlField.setEnabled(true);
        policyCombo.setEnabled(true);
        modeCombo.setEnabled(true);
        statusLabel.setText("Disconnected");
        statusLabel.setForeground(Color.GRAY);
    }

    public void setError(String message) {
        connected = false;
        connectButton.setEnabled(true);
        connectButton.setText("Connect");
        urlField.setEnabled(true);
        policyCombo.setEnabled(true);
        modeCombo.setEnabled(true);
        statusLabel.setText("Error ✕");
        statusLabel.setForeground(Color.RED);
    }

    // cell renderers

    /** Shows the SecurityPolicy enum name in the combo box. */
    private static class PolicyRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int idx, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, idx, isSelected, cellHasFocus);
            if (value instanceof SecurityPolicy sp) setText(sp.name());
            return this;
        }
    }

    /** Shows the MessageSecurityMode enum name in the combo box. */
    private static class ModeRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int idx, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, idx, isSelected, cellHasFocus);
            if (value instanceof MessageSecurityMode m) setText(m.toString());
            return this;
        }
    }
}