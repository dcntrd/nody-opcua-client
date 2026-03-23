package net.decentered.nody.opcua.client.ui;

import net.decentered.nody.opcua.client.model.AuthMode;
import net.decentered.nody.opcua.client.model.ConnectionConfig;
import net.decentered.nody.opcua.client.model.ConnectionProfile;
import net.decentered.nody.opcua.client.settings.ConnectionProfileStore;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Dialog for connecting to an OPC UA server without first saving a profile.
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>Name (used if the user saves as a profile; defaults to the URL)</li>
 *   <li>URL</li>
 *   <li>Security Policy + Message Security Mode</li>
 *   <li>Identity: Anonymous / Username+Password / User Certificate</li>
 * </ul>
 *
 * <h3>Actions</h3>
 * <ul>
 *   <li><b>Connect</b> – build a {@link ConnectionConfig} and fire
 *       {@code onConnect} without persisting anything</li>
 *   <li><b>Save as Profile &amp; Connect</b> – save to the
 *       {@link ConnectionProfileStore} first, then connect</li>
 *   <li><b>Cancel</b> – close without connecting</li>
 * </ul>
 *
 * <p>Opened via <b>Connections → Quick Connect…</b>.</p>
 */
public class AdHocConnectionDialog extends JDialog {

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

    private static final String CARD_NONE      = "none";
    private static final String CARD_USER_PASS = "userpass";
    private static final String CARD_USER_CERT = "usercert";

    // ── Widgets ───────────────────────────────────────────────────────────────
    private final JTextField                     nameField   = new JTextField(30);
    private final JTextField                     urlField;
    private final JComboBox<SecurityPolicy>      policyCombo = new JComboBox<>(POLICIES);
    private final JComboBox<MessageSecurityMode> modeCombo   = new JComboBox<>(MODES_NONE);

    private final JRadioButton anonymousRadio = new JRadioButton("Anonymous");
    private final JRadioButton userPassRadio  = new JRadioButton("Username / Password");
    private final JRadioButton userCertRadio  = new JRadioButton("User Certificate");

    private final JTextField     usernameField     = new JTextField(14);
    private final JPasswordField passwordField     = new JPasswordField(14);
    private final JTextField     certPathField;
    private final JPasswordField certPasswordField = new JPasswordField(12);

    private final JPanel     credCards;
    private final CardLayout credLayout = new CardLayout();

    // ── Collaborators ─────────────────────────────────────────────────────────
    private final ConnectionProfileStore store;

    // ── Result ────────────────────────────────────────────────────────────────
    /**
     * Non-null after the user confirmed; {@code null} if they cancelled.
     * Retrieve after {@code setVisible(true)} returns via {@link #getResult()}.
     */
    private ConnectionConfig result = null;

    // ── Construction ──────────────────────────────────────────────────────────

    /**
     * Opens with the default URL {@code opc.tcp://localhost:4840}.
     * Used from <b>Connections → Quick Connect…</b>.
     */
    public AdHocConnectionDialog(Frame owner, ConnectionProfileStore store) {
        this(owner, "opc.tcp://localhost:4840", store);
    }

    /**
     * Opens with {@code initialUrl} pre-filled in the URL field.
     * Used by {@link QuickConnectBar} when the user types a URL that is
     * not a saved profile.
     *
     * @param owner      parent frame
     * @param initialUrl URL to pre-fill (e.g. what the user typed)
     * @param store      profile store used for "Save as Profile &amp; Connect"
     */
    public AdHocConnectionDialog(Frame owner,
                                 String initialUrl,
                                 ConnectionProfileStore store) {
        super(owner, "Quick Connect", true);
        this.store = store;

        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        urlField      = new JTextField(initialUrl != null ? initialUrl : "opc.tcp://localhost:4840", 30);
        urlField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        certPathField = new JTextField(22);
        certPathField.setEditable(false);
        certPathField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        // ── URL auto-fills name ───────────────────────────────────────────────
        urlField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { syncName(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { syncName(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { syncName(); }
            private void syncName() {
                if (!nameField.isEdited()) nameField.setText(urlField.getText().trim());
            }
        });

        // ── Credential cards ──────────────────────────────────────────────────
        credCards = new JPanel(credLayout);

        credCards.add(new JPanel(), CARD_NONE);

        JPanel upCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        upCard.add(lbl("Username:")); upCard.add(usernameField);
        upCard.add(Box.createHorizontalStrut(6));
        upCard.add(lbl("Password:")); upCard.add(passwordField);
        credCards.add(upCard, CARD_USER_PASS);

        JPanel certCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        certCard.add(lbl("PKCS#12:")); certCard.add(certPathField);
        JButton browseBtn = new JButton("Browse…");
        browseBtn.addActionListener(e -> browseCert());
        certCard.add(browseBtn);
        certCard.add(Box.createHorizontalStrut(4));
        certCard.add(lbl("Password:")); certCard.add(certPasswordField);
        credCards.add(certCard, CARD_USER_CERT);

        // ── Form ──────────────────────────────────────────────────────────────
        JPanel form = buildForm();
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));

        // ── Buttons ───────────────────────────────────────────────────────────
        JButton connectBtn     = new JButton("Connect");
        JButton saveConnectBtn = new JButton("Save as Profile & Connect");
        JButton cancelBtn      = new JButton("Cancel");

        connectBtn.setFont(connectBtn.getFont().deriveFont(Font.BOLD));
        saveConnectBtn.setToolTipText("Save this connection to the profile list, then connect");

        connectBtn.addActionListener(e     -> handleConnect(false));
        saveConnectBtn.addActionListener(e -> handleConnect(true));
        cancelBtn.addActionListener(e     -> dispose());

        // Make Connect the default button (Enter key)
        getRootPane().setDefaultButton(connectBtn);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.add(cancelBtn);
        buttons.add(saveConnectBtn);
        buttons.add(connectBtn);
        buttons.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));

        // ── Layout ────────────────────────────────────────────────────────────
        setLayout(new BorderLayout());
        add(form,    BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        // ── Wiring ────────────────────────────────────────────────────────────
        ButtonGroup grp = new ButtonGroup();
        grp.add(anonymousRadio); grp.add(userPassRadio); grp.add(userCertRadio);
        anonymousRadio.setSelected(true);

        policyCombo.setRenderer(new PolicyRenderer());
        modeCombo.setRenderer(new ModeRenderer());
        policyCombo.addActionListener(e -> syncModeCombo());
        anonymousRadio.addActionListener(e -> syncCredCards());
        userPassRadio.addActionListener(e -> syncCredCards());
        userCertRadio.addActionListener(e -> syncCredCards());

        pack();
        setLocationRelativeTo(owner);
        urlField.selectAll();
        urlField.requestFocusInWindow();
    }

    // ── Whether the dialog was confirmed ─────────────────────────────────────
    /**
     * Returns the {@link ConnectionConfig} the user confirmed, or {@code null}
     * if the dialog was cancelled.  Call after {@code setVisible(true)} returns.
     */
    public ConnectionConfig getResult() { return result; }

    // ── Form builder ─────────────────────────────────────────────────────────

    private JPanel buildForm() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints lc = lcon(); GridBagConstraints fc = fcon();
        int row = 0;

        addRow(p, "Name:",            nameField,   row++, lc, fc);
        addRow(p, "URL *:",           urlField,    row++, lc, fc);
        addRow(p, "Security Policy:", policyCombo, row++, lc, fc);
        addRow(p, "Security Mode:",   modeCombo,   row++, lc, fc);

        // Identity section header
        GridBagConstraints sc = new GridBagConstraints();
        sc.gridx = 0; sc.gridy = row++; sc.gridwidth = 2;
        sc.anchor = GridBagConstraints.WEST; sc.insets = new Insets(10, 2, 2, 0);
        JLabel secHdr = new JLabel("Identity");
        secHdr.setFont(secHdr.getFont().deriveFont(Font.BOLD));
        secHdr.setForeground(Color.DARK_GRAY);
        p.add(secHdr, sc);

        // Radio row
        JPanel radioRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        radioRow.add(anonymousRadio); radioRow.add(userPassRadio); radioRow.add(userCertRadio);
        GridBagConstraints rrc = fcon(); rrc.gridy = row++;
        p.add(radioRow, rrc);

        // Credential cards row
        GridBagConstraints crc = fcon(); crc.gridy = row++;
        p.add(credCards, crc);

        return p;
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    private void handleConnect(boolean saveProfile) {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter an OPC UA endpoint URL.",
                    "Missing URL", JOptionPane.WARNING_MESSAGE);
            return;
        }

        SecurityPolicy      policy   = (SecurityPolicy)      policyCombo.getSelectedItem();
        MessageSecurityMode mode     = (MessageSecurityMode) modeCombo.getSelectedItem();
        AuthMode            authMode = selectedAuth();
        String              username = usernameField.getText().trim();
        char[]              password = passwordField.getPassword();
        String              certPath = certPathField.getText().trim();
        char[]              certPw   = certPasswordField.getPassword();

        if (authMode == AuthMode.USERNAME_PASSWORD && username.isBlank()) {
            Arrays.fill(password, '\0'); Arrays.fill(certPw, '\0');
            JOptionPane.showMessageDialog(this, "Please enter a username.",
                    "Missing Username", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (authMode == AuthMode.USER_CERTIFICATE && certPath.isBlank()) {
            Arrays.fill(password, '\0'); Arrays.fill(certPw, '\0');
            JOptionPane.showMessageDialog(this,
                    "Please select a PKCS#12 file for User Certificate authentication.",
                    "Missing Certificate", JOptionPane.WARNING_MESSAGE);
            return;
        }

        char[] userPw = (authMode == AuthMode.USERNAME_PASSWORD) ? password : new char[0];
        char[] ksPw   = (authMode == AuthMode.USER_CERTIFICATE)  ? certPw   : new char[0];

        ConnectionConfig cfg = new ConnectionConfig(
                url, policy, mode, authMode, username, userPw,
                certPath.isEmpty() ? null : Path.of(certPath), ksPw);

        Arrays.fill(password, '\0');
        Arrays.fill(certPw,   '\0');

        if (saveProfile) {
            String profileName = nameField.getText().trim();
            if (profileName.isEmpty()) profileName = url;
            ConnectionProfile p = new ConnectionProfile(profileName, url);
            p.setSecurityPolicy(policy);
            p.setSecurityMode(mode);
            p.setAuthMode(authMode);
            p.setUsername(username);
            p.setUserCertPath(certPath);
            store.addProfile(p);
        }

        result = cfg;
        dispose();
    }

    // ── Sync helpers ──────────────────────────────────────────────────────────

    private void syncModeCombo() {
        SecurityPolicy policy = (SecurityPolicy) policyCombo.getSelectedItem();
        MessageSecurityMode current = (MessageSecurityMode) modeCombo.getSelectedItem();
        MessageSecurityMode[] modes =
                (policy == SecurityPolicy.None) ? MODES_NONE : MODES_SECURE;
        modeCombo.removeAllItems();
        for (var m : modes) modeCombo.addItem(m);
        boolean restored = false;
        for (var m : modes) {
            if (m == current) { modeCombo.setSelectedItem(m); restored = true; break; }
        }
        if (!restored) modeCombo.setSelectedIndex(0);
    }

    private void syncCredCards() {
        if      (userPassRadio.isSelected()) credLayout.show(credCards, CARD_USER_PASS);
        else if (userCertRadio.isSelected()) credLayout.show(credCards, CARD_USER_CERT);
        else                                 credLayout.show(credCards, CARD_NONE);
    }

    private AuthMode selectedAuth() {
        if (userPassRadio.isSelected()) return AuthMode.USERNAME_PASSWORD;
        if (userCertRadio.isSelected()) return AuthMode.USER_CERTIFICATE;
        return AuthMode.ANONYMOUS;
    }

    private void browseCert() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select PKCS#12 User Certificate");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "PKCS#12 keystore (*.p12, *.pfx)", "p12", "pfx"));
        if (!certPathField.getText().isBlank())
            fc.setCurrentDirectory(new java.io.File(certPathField.getText()).getParentFile());
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            certPathField.setText(fc.getSelectedFile().getAbsolutePath());
    }

    // ── GridBag helpers ───────────────────────────────────────────────────────

    private static GridBagConstraints lcon() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(3, 4, 3, 8); return c;
    }
    private static GridBagConstraints fcon() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        c.insets = new Insets(3, 0, 3, 4); return c;
    }
    private static void addRow(JPanel p, String label, Component field,
                               int row, GridBagConstraints lc, GridBagConstraints fc) {
        lc.gridy = row; fc.gridy = row;
        p.add(new JLabel(label), lc); p.add(field, fc);
    }
    private static JLabel lbl(String t) { return new JLabel(t); }

    // ── Renderers ─────────────────────────────────────────────────────────────

    private static class PolicyRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> l, Object v,
                                                                int i, boolean s, boolean f) {
            super.getListCellRendererComponent(l, v, i, s, f);
            if (v instanceof SecurityPolicy sp) setText(sp.name()); return this;
        }
    }
    private static class ModeRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> l, Object v,
                                                                int i, boolean s, boolean f) {
            super.getListCellRendererComponent(l, v, i, s, f);
            if (v instanceof MessageSecurityMode m) setText(m.toString()); return this;
        }
    }

    // ── Edited-aware text field ───────────────────────────────────────────────

    /**
     * A {@link JTextField} that tracks whether the user has manually edited
     * the content (used to avoid overwriting a custom name with the URL).
     */
    private static final class JTextField extends javax.swing.JTextField {
        private boolean edited = false;
        JTextField(int cols) { super(cols); }
        JTextField(String text, int cols) { super(text, cols); }
        boolean isEdited() { return edited; }

        @Override protected void processKeyEvent(java.awt.event.KeyEvent e) {
            super.processKeyEvent(e);
            if (e.getID() == java.awt.event.KeyEvent.KEY_TYPED) edited = true;
        }
    }
}