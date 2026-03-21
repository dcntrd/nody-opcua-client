package net.decentered.nody.opcua.client.ui;

import net.decentered.nody.opcua.client.model.AuthMode;
import net.decentered.nody.opcua.client.model.ConnectionConfig;
import net.decentered.nody.opcua.client.settings.ConnectionSettings;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Top panel: endpoint URL, security policy/mode, identity and Connect button.
 *
 * <h3>Layout (4 rows)</h3>
 * <pre>
 *  Row 1: [ OPC UA URL: _________________________ ]
 *  Row 2: [ Security Policy: [combo]  Message Security Mode: [combo] ]
 *  Row 3: [ Identity: (o)Anonymous (o)User/Pass (o)User Cert | [credential card] ]
 *  Row 4: [ [Connect]  status label ]
 * </pre>
 * The Connect/Disconnect button lives in its own dedicated row (row 4) so it
 * is always fully visible regardless of which credential card is showing.
 *
 * <h3>Credential cards (CardLayout)</h3>
 * <ul>
 *   <li><b>Anonymous</b> – empty panel</li>
 *   <li><b>Username / Password</b> – username + password fields</li>
 *   <li><b>User Certificate</b> – PKCS#12 file chooser + keystore password</li>
 * </ul>
 */
public class ConnectionPanel extends JPanel {

    // ── Security options ──────────────────────────────────────────────────────
    private static final SecurityPolicy[] POLICIES = {
            SecurityPolicy.None,
            SecurityPolicy.Basic128Rsa15,
            SecurityPolicy.Basic256,
            SecurityPolicy.Basic256Sha256,
            SecurityPolicy.Aes128_Sha256_RsaOaep,
            SecurityPolicy.Aes256_Sha256_RsaPss
    };
    private static final MessageSecurityMode[] MODES_NONE   = { MessageSecurityMode.None };
    private static final MessageSecurityMode[] MODES_SECURE = {
            MessageSecurityMode.Sign, MessageSecurityMode.SignAndEncrypt
    };

    // ── Card names ────────────────────────────────────────────────────────────
    private static final String CARD_NONE      = "none";
    private static final String CARD_USER_PASS = "userpass";
    private static final String CARD_USER_CERT = "usercert";

    // ── Widgets ───────────────────────────────────────────────────────────────
    private final JTextField                     urlField;
    private final JComboBox<SecurityPolicy>      policyCombo;
    private final JComboBox<MessageSecurityMode> modeCombo;

    private final JRadioButton anonymousRadio;
    private final JRadioButton userPassRadio;
    private final JRadioButton userCertRadio;

    private final JTextField     usernameField;
    private final JPasswordField passwordField;

    private final JTextField     certPathField;
    private final JPasswordField certPasswordField;

    private final JPanel     credentialCards;
    private final CardLayout cardLayout;

    // Always-visible bottom row
    private final JButton connectButton;
    private final JLabel  statusLabel;

    // ── Collaborators / callbacks ─────────────────────────────────────────────
    private final ConnectionSettings         settings;
    private final Consumer<ConnectionConfig> onConnect;
    private final Runnable                   onDisconnect;
    private boolean connected = false;

    // ── Construction ──────────────────────────────────────────────────────────

    public ConnectionPanel(ConnectionSettings settings,
                           Consumer<ConnectionConfig> onConnect,
                           Runnable onDisconnect) {
        this.settings     = settings;
        this.onConnect    = onConnect;
        this.onDisconnect = onDisconnect;

        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        // The top section uses a 3-row GridLayout (URL / security / identity)
        JPanel topRows = new JPanel(new GridLayout(3, 1, 0, 4));
        topRows.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        // ── Row 1: URL ────────────────────────────────────────────────────────
        JPanel urlRow = new JPanel(new BorderLayout(6, 0));
        urlRow.add(labelOf("OPC UA URL:"), BorderLayout.WEST);
        urlField = new JTextField(settings.getUrl(), 50);
        urlField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        urlRow.add(urlField, BorderLayout.CENTER);

        // ── Row 2: Security policy + mode ─────────────────────────────────────
        JPanel secRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        secRow.add(labelOf("Security Policy:"));
        policyCombo = new JComboBox<>(POLICIES);
        policyCombo.setRenderer(new PolicyRenderer());
        policyCombo.setSelectedItem(settings.getSecurityPolicy());
        secRow.add(policyCombo);
        secRow.add(Box.createHorizontalStrut(10));
        secRow.add(labelOf("Message Security Mode:"));
        modeCombo = new JComboBox<>(modesFor(settings.getSecurityPolicy()));
        modeCombo.setRenderer(new ModeRenderer());
        modeCombo.setSelectedItem(settings.getSecurityMode());
        if (modeCombo.getSelectedIndex() < 0) modeCombo.setSelectedIndex(0);
        secRow.add(modeCombo);

        // ── Row 3: Identity radios + credential cards ─────────────────────────
        JPanel authRow = new JPanel(new BorderLayout(8, 0));

        // Radios on the left
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        ButtonGroup authGroup = new ButtonGroup();
        anonymousRadio = new JRadioButton("Anonymous");
        userPassRadio  = new JRadioButton("Username / Password");
        userCertRadio  = new JRadioButton("User Certificate");
        authGroup.add(anonymousRadio);
        authGroup.add(userPassRadio);
        authGroup.add(userCertRadio);
        switch (settings.getAuthMode()) {
            case USERNAME_PASSWORD -> userPassRadio.setSelected(true);
            case USER_CERTIFICATE  -> userCertRadio.setSelected(true);
            default                -> anonymousRadio.setSelected(true);
        }
        radioPanel.add(labelOf("Identity:"));
        radioPanel.add(anonymousRadio);
        radioPanel.add(userPassRadio);
        radioPanel.add(userCertRadio);
        authRow.add(radioPanel, BorderLayout.WEST);

        // Credential cards fill the remaining space on the right
        cardLayout      = new CardLayout();
        credentialCards = new JPanel(cardLayout);

        // Card: anonymous (empty)
        credentialCards.add(new JPanel(), CARD_NONE);

        // Card: username + password
        JPanel upCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        upCard.add(labelOf("Username:"));
        usernameField = new JTextField(settings.getUsername(), 12);
        upCard.add(usernameField);
        upCard.add(Box.createHorizontalStrut(4));
        upCard.add(labelOf("Password:"));
        passwordField = new JPasswordField(12);
        upCard.add(passwordField);
        credentialCards.add(upCard, CARD_USER_PASS);

        // Card: user certificate
        JPanel certCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        certCard.add(labelOf("PKCS#12:"));
        certPathField = new JTextField(settings.getUserCertPath(), 22);
        certPathField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        certPathField.setEditable(false);
        certCard.add(certPathField);
        JButton browseBtn = new JButton("Browse…");
        browseBtn.addActionListener(e -> browseCertFile());
        certCard.add(browseBtn);
        certCard.add(Box.createHorizontalStrut(4));
        certCard.add(labelOf("Keystore Password:"));
        certPasswordField = new JPasswordField(10);
        certCard.add(certPasswordField);
        credentialCards.add(certCard, CARD_USER_CERT);

        authRow.add(credentialCards, BorderLayout.CENTER);

        topRows.add(urlRow);
        topRows.add(secRow);
        topRows.add(authRow);

        // ── Row 4: Connect button + status (always visible, pinned to bottom) ──
        connectButton = new JButton("Connect");
        connectButton.setPreferredSize(new Dimension(120, 28));

        statusLabel = new JLabel("Disconnected");
        statusLabel.setForeground(Color.GRAY);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonRow.add(connectButton);
        buttonRow.add(statusLabel);

        add(topRows,    BorderLayout.CENTER);
        add(buttonRow,  BorderLayout.SOUTH);

        // ── Wiring ────────────────────────────────────────────────────────────
        policyCombo.addActionListener(e -> syncModeCombo());
        anonymousRadio.addActionListener(e -> syncAuthFields());
        userPassRadio.addActionListener(e -> syncAuthFields());
        userCertRadio.addActionListener(e -> syncAuthFields());
        connectButton.addActionListener(e -> handleButtonClick());

        syncAuthFields();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static MessageSecurityMode[] modesFor(SecurityPolicy policy) {
        return (policy == SecurityPolicy.None) ? MODES_NONE : MODES_SECURE;
    }

    private void syncModeCombo() {
        MessageSecurityMode current = (MessageSecurityMode) modeCombo.getSelectedItem();
        MessageSecurityMode[] modes = modesFor(selectedPolicy());
        modeCombo.removeAllItems();
        for (MessageSecurityMode m : modes) modeCombo.addItem(m);
        boolean restored = false;
        for (MessageSecurityMode m : modes) {
            if (m == current) { modeCombo.setSelectedItem(m); restored = true; break; }
        }
        if (!restored) modeCombo.setSelectedIndex(0);
    }

    private void syncAuthFields() {
        if (userPassRadio.isSelected())      cardLayout.show(credentialCards, CARD_USER_PASS);
        else if (userCertRadio.isSelected()) cardLayout.show(credentialCards, CARD_USER_CERT);
        else                                 cardLayout.show(credentialCards, CARD_NONE);
    }

    private void browseCertFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select PKCS#12 User Certificate");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "PKCS#12 keystore (*.p12, *.pfx)", "p12", "pfx"));
        if (!certPathField.getText().isBlank())
            fc.setCurrentDirectory(new java.io.File(certPathField.getText()).getParentFile());
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            certPathField.setText(fc.getSelectedFile().getAbsolutePath());
    }

    private void handleButtonClick() {
        if (!connected) {
            String url = urlField.getText().trim();
            if (url.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter an OPC UA endpoint URL.",
                        "Missing URL", JOptionPane.WARNING_MESSAGE);
                return;
            }

            SecurityPolicy      policy   = selectedPolicy();
            MessageSecurityMode mode     = selectedMode();
            AuthMode            authMode = selectedAuthMode();
            String              username = usernameField.getText().trim();
            char[]              password = passwordField.getPassword();
            String              certPath = certPathField.getText().trim();
            char[]              certPw   = certPasswordField.getPassword();

            ConnectionConfig cfg = new ConnectionConfig(
                    url, policy, mode, authMode, username, password,
                    certPath.isEmpty() ? null : Path.of(certPath), certPw);

            java.util.Arrays.fill(password, '\0');
            java.util.Arrays.fill(certPw,   '\0');

            if (!cfg.isSecurityValid()) {
                cfg.clearSecrets();
                JOptionPane.showMessageDialog(this,
                        "Invalid combination: policy '" + policy.name() +
                                "' cannot be used with mode '" + mode + "'.",
                        "Invalid Security Settings", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (cfg.isMissingUsername()) {
                cfg.clearSecrets();
                JOptionPane.showMessageDialog(this,
                        "Please enter a username for Username / Password authentication.",
                        "Missing Username", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (cfg.isMissingUserCert()) {
                cfg.clearSecrets();
                JOptionPane.showMessageDialog(this,
                        "Please select a PKCS#12 keystore for User Certificate authentication.",
                        "Missing Certificate", JOptionPane.WARNING_MESSAGE);
                return;
            }

            settings.save(url, policy, mode, authMode, username, certPath);
            setConnecting();
            onConnect.accept(cfg);

        } else {
            setDisconnecting();
            onDisconnect.run();
        }
    }

    private SecurityPolicy      selectedPolicy()   { return (SecurityPolicy)      policyCombo.getSelectedItem(); }
    private MessageSecurityMode selectedMode()     { return (MessageSecurityMode) modeCombo.getSelectedItem(); }
    private AuthMode            selectedAuthMode() {
        if (userPassRadio.isSelected()) return AuthMode.USERNAME_PASSWORD;
        if (userCertRadio.isSelected()) return AuthMode.USER_CERTIFICATE;
        return AuthMode.ANONYMOUS;
    }

    private static JLabel labelOf(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.PLAIN));
        return l;
    }

    // ── Public state methods (EDT) ────────────────────────────────────────────

    public void setConnecting() {
        connectButton.setEnabled(false);
        connectButton.setText("Connecting…");
        setInputsEnabled(false);
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
        setInputsEnabled(true);
        statusLabel.setText("Disconnected");
        statusLabel.setForeground(Color.GRAY);
    }

    public void setError(String message) {
        connected = false;
        connectButton.setEnabled(true);
        connectButton.setText("Connect");
        setInputsEnabled(true);
        statusLabel.setText("Error ✕");
        statusLabel.setForeground(Color.RED);
    }

    private void setInputsEnabled(boolean enabled) {
        urlField.setEnabled(enabled);
        policyCombo.setEnabled(enabled);
        modeCombo.setEnabled(enabled);
        anonymousRadio.setEnabled(enabled);
        userPassRadio.setEnabled(enabled);
        userCertRadio.setEnabled(enabled);
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        certPathField.setEnabled(enabled);
        certPasswordField.setEnabled(enabled);
    }

    // ── Cell renderers ────────────────────────────────────────────────────────

    private static class PolicyRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                                                                int idx, boolean sel, boolean focus) {
            super.getListCellRendererComponent(list, value, idx, sel, focus);
            if (value instanceof SecurityPolicy sp) setText(sp.name());
            return this;
        }
    }

    private static class ModeRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                                                                int idx, boolean sel, boolean focus) {
            super.getListCellRendererComponent(list, value, idx, sel, focus);
            if (value instanceof MessageSecurityMode m) setText(m.toString());
            return this;
        }
    }
}