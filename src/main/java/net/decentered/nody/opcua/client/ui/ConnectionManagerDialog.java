package net.decentered.nody.opcua.client.ui;

import net.decentered.nody.opcua.client.model.AuthMode;
import net.decentered.nody.opcua.client.model.ConnectionProfile;
import net.decentered.nody.opcua.client.settings.ConnectionProfileStore;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Modal dialog for managing saved OPC UA connection profiles.
 *
 * <h3>Layout</h3>
 * <pre>
 * ┌────────────────┬──────────────────────────────────────────┐
 * │  Profile list  │  Editor form                             │
 * │  ────────────  │  Name: [___________]                     │
 * │  > Prosys      │  URL:  [___________________________]     │
 * │    localhost   │  Security Policy: [combo]                │
 * │                │  Security Mode:   [combo]                │
 * │  [+][Clone][X] │  ── Identity ─────────────────────────── │
 * │                │  (o)Anonymous (o)User/Pass (o)User Cert  │
 * │                │  Username: [____]  (shown for User/Pass) │
 * │                │  PKCS#12:  [____] [Browse] (User Cert)   │
 * │                │                                          │
 * │                │  [Save]                                  │
 * └────────────────┴──────────────────────────────────────────┘
 * </pre>
 * Changes are auto-applied to the store.  The combo box in the main window
 * is refreshed when this dialog is closed.
 */
public class ConnectionManagerDialog extends JDialog {

    // ── Security combos ───────────────────────────────────────────────────────
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

    // ── Store ─────────────────────────────────────────────────────────────────
    private final ConnectionProfileStore store;

    // ── List panel ────────────────────────────────────────────────────────────
    private final DefaultListModel<ConnectionProfile> listModel = new DefaultListModel<>();
    private final JList<ConnectionProfile>            profileList;

    // ── Editor form ───────────────────────────────────────────────────────────
    private final JTextField                     nameField    = new JTextField(20);
    private final JTextField                     urlField     = new JTextField(34);
    private final JComboBox<SecurityPolicy>      policyCombo  = new JComboBox<>(POLICIES);
    private final JComboBox<MessageSecurityMode> modeCombo    = new JComboBox<>(MODES_NONE);

    private final JRadioButton anonymousRadio = new JRadioButton("Anonymous");
    private final JRadioButton userPassRadio  = new JRadioButton("Username / Password");
    private final JRadioButton userCertRadio  = new JRadioButton("User Certificate");

    private final JTextField   usernameField  = new JTextField(14);
    private final JTextField   certPathField  = initCertPathField();

    private final CardLayout credLayout = new CardLayout();
    private final JPanel     credCards  = new JPanel(credLayout);

    private final JButton saveButton   = new JButton("Save");

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean suppressSelectionEvents = false;

    // ── Construction ──────────────────────────────────────────────────────────

    public ConnectionManagerDialog(Frame owner, ConnectionProfileStore store) {
        super(owner, "Connection Manager", true);
        this.store = store;
        setSize(780, 480);
        setMinimumSize(new Dimension(640, 400));
        setLocationRelativeTo(owner);

        // ── Left: profile list ────────────────────────────────────────────────
        profileList = new JList<>(listModel);
        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profileList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        profileList.setFixedCellHeight(26);
        refreshList();

        JScrollPane listScroll = new JScrollPane(profileList);
        listScroll.setPreferredSize(new Dimension(200, 0));

        JButton addBtn   = new JButton("+");
        JButton cloneBtn = new JButton("Clone");
        JButton delBtn   = new JButton("Delete");
        addBtn.setToolTipText("Add a new connection profile");
        cloneBtn.setToolTipText("Duplicate the selected profile");
        delBtn.setToolTipText("Delete the selected profile");
        addBtn.setFont(addBtn.getFont().deriveFont(Font.BOLD, 14f));

        JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        listButtons.add(addBtn);
        listButtons.add(cloneBtn);
        listButtons.add(delBtn);

        JPanel leftPanel = new JPanel(new BorderLayout(0, 4));
        leftPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Profiles",
                TitledBorder.LEFT, TitledBorder.TOP));
        leftPanel.add(listScroll,   BorderLayout.CENTER);
        leftPanel.add(listButtons,  BorderLayout.SOUTH);

        // ── Right: editor form ────────────────────────────────────────────────
        JPanel editorPanel = buildEditorPanel();
        editorPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Profile Details",
                TitledBorder.LEFT, TitledBorder.TOP));

        // ── Bottom: close ─────────────────────────────────────────────────────
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(closeBtn);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, editorPanel);
        split.setDividerLocation(210);
        split.setResizeWeight(0.0);

        setLayout(new BorderLayout());
        add(split,  BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // ── Wiring ────────────────────────────────────────────────────────────
        profileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !suppressSelectionEvents) {
                loadSelectedProfile();
            }
        });

        // Double-click: focus name field for quick rename
        profileList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) nameField.requestFocusInWindow();
            }
        });

        addBtn.addActionListener(e -> addProfile());
        cloneBtn.addActionListener(e -> cloneProfile());
        delBtn.addActionListener(e -> deleteProfile());
        saveButton.addActionListener(e -> saveProfile());

        policyCombo.setRenderer(new PolicyRenderer());
        modeCombo.setRenderer(new ModeRenderer());
        policyCombo.addActionListener(e -> syncModeCombo());
        anonymousRadio.addActionListener(e -> syncCredCards());
        userPassRadio.addActionListener(e -> syncCredCards());
        userCertRadio.addActionListener(e -> syncCredCards());

        // Select first profile
        if (!listModel.isEmpty()) profileList.setSelectedIndex(store.getSelectedIndex());
        loadSelectedProfile();
    }

    private static JTextField initCertPathField() {
        JTextField f = new JTextField(20);
        f.setEditable(false);
        f.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return f;
    }

    // ── Editor panel builder ──────────────────────────────────────────────────

    private JPanel buildEditorPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints lc = lcon(); GridBagConstraints fc = fcon();

        int row = 0;
        addRow(p, "Name:",             nameField,   row++, lc, fc);
        addRow(p, "URL:",              urlField,    row++, lc, fc);
        addRow(p, "Security Policy:",  policyCombo, row++, lc, fc);
        addRow(p, "Security Mode:",    modeCombo,   row++, lc, fc);

        // ── Identity section ──────────────────────────────────────────────────
        GridBagConstraints sc = sectionCon(row++);
        p.add(sectionLabel("Identity"), sc);

        // Radio buttons row
        ButtonGroup grp = new ButtonGroup();
        grp.add(anonymousRadio); grp.add(userPassRadio); grp.add(userCertRadio);
        JPanel radioRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        radioRow.add(anonymousRadio); radioRow.add(userPassRadio); radioRow.add(userCertRadio);
        GridBagConstraints rc = fcon(); rc.gridy = row++;
        p.add(radioRow, rc);

        // Credential cards
        credCards.add(new JPanel(), CARD_NONE);

        JPanel upCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        upCard.add(label("Username:")); upCard.add(usernameField);
        credCards.add(upCard, CARD_USER_PASS);

        JButton browseBtn = new JButton("Browse…");
        browseBtn.addActionListener(e -> browseCert());
        JPanel certCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        certCard.add(label("PKCS#12:")); certCard.add(certPathField); certCard.add(browseBtn);
        credCards.add(certCard, CARD_USER_CERT);

        GridBagConstraints cc = fcon(); cc.gridy = row++;
        p.add(credCards, cc);

        // Spacer
        GridBagConstraints spacer = new GridBagConstraints();
        spacer.gridy = row++; spacer.weighty = 1.0; spacer.fill = GridBagConstraints.VERTICAL;
        p.add(Box.createVerticalGlue(), spacer);

        // Save button
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridy = row; bc.gridx = 0; bc.gridwidth = 2;
        bc.anchor = GridBagConstraints.EAST; bc.insets = new Insets(6, 0, 0, 0);
        saveButton.setFont(saveButton.getFont().deriveFont(Font.BOLD));
        p.add(saveButton, bc);

        return p;
    }

    // ── List actions ─────────────────────────────────────────────────────────

    private void addProfile() {
        String url = "opc.tcp://localhost:4840";
        ConnectionProfile p = new ConnectionProfile(url, url);
        store.addProfile(p);
        refreshList();
        profileList.setSelectedIndex(listModel.size() - 1);
        nameField.selectAll();
        nameField.requestFocusInWindow();
    }

    private void cloneProfile() {
        ConnectionProfile sel = profileList.getSelectedValue();
        if (sel == null) return;
        ConnectionProfile copy = sel.duplicate();
        store.addProfile(copy);
        refreshList();
        profileList.setSelectedIndex(listModel.size() - 1);
    }

    private void deleteProfile() {
        ConnectionProfile sel = profileList.getSelectedValue();
        if (sel == null) return;
        if (listModel.size() == 1) {
            JOptionPane.showMessageDialog(this,
                    "You must have at least one connection profile.",
                    "Cannot Delete", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete profile \"" + sel.getName() + "\"?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        int idx = profileList.getSelectedIndex();
        store.deleteProfile(sel.getId());
        refreshList();
        profileList.setSelectedIndex(Math.min(idx, listModel.size() - 1));
    }

    private void saveProfile() {
        ConnectionProfile sel = profileList.getSelectedValue();
        if (sel == null) return;
        applyFormToProfile(sel);
        store.updateProfile(sel);
        // Refresh label in list (name may have changed)
        suppressSelectionEvents = true;
        int idx = profileList.getSelectedIndex();
        refreshList();
        profileList.setSelectedIndex(idx);
        suppressSelectionEvents = false;
        setTitle("Connection Manager");  // clear any "modified" indicator
    }

    // ── Form sync ─────────────────────────────────────────────────────────────

    private void loadSelectedProfile() {
        ConnectionProfile p = profileList.getSelectedValue();
        if (p == null) return;
        store.setSelectedIndex(profileList.getSelectedIndex());

        suppressSelectionEvents = true;
        nameField.setText(p.getName());
        urlField.setText(p.getEndpointUrl());
        policyCombo.setSelectedItem(p.getSecurityPolicy());
        syncModeCombo();
        modeCombo.setSelectedItem(p.getSecurityMode());
        if (modeCombo.getSelectedIndex() < 0) modeCombo.setSelectedIndex(0);

        switch (p.getAuthMode()) {
            case USERNAME_PASSWORD -> userPassRadio.setSelected(true);
            case USER_CERTIFICATE  -> userCertRadio.setSelected(true);
            default                -> anonymousRadio.setSelected(true);
        }
        usernameField.setText(p.getUsername());
        certPathField.setText(p.getUserCertPath());
        syncCredCards();
        suppressSelectionEvents = false;
    }

    private void applyFormToProfile(ConnectionProfile p) {
        p.setName(nameField.getText().trim().isEmpty()
                ? urlField.getText().trim() : nameField.getText().trim());
        p.setEndpointUrl(urlField.getText().trim());
        p.setSecurityPolicy((SecurityPolicy) policyCombo.getSelectedItem());
        p.setSecurityMode((MessageSecurityMode) modeCombo.getSelectedItem());
        p.setUsername(usernameField.getText().trim());
        p.setUserCertPath(certPathField.getText().trim());
        if      (userPassRadio.isSelected()) p.setAuthMode(AuthMode.USERNAME_PASSWORD);
        else if (userCertRadio.isSelected()) p.setAuthMode(AuthMode.USER_CERTIFICATE);
        else                                 p.setAuthMode(AuthMode.ANONYMOUS);
    }

    private void syncModeCombo() {
        SecurityPolicy policy = (SecurityPolicy) policyCombo.getSelectedItem();
        MessageSecurityMode current = (MessageSecurityMode) modeCombo.getSelectedItem();
        MessageSecurityMode[] modes = (policy == SecurityPolicy.None) ? MODES_NONE : MODES_SECURE;
        modeCombo.removeAllItems();
        for (var m : modes) modeCombo.addItem(m);
        boolean restored = false;
        for (var m : modes) { if (m == current) { modeCombo.setSelectedItem(m); restored = true; break; } }
        if (!restored) modeCombo.setSelectedIndex(0);
    }

    private void syncCredCards() {
        if      (userPassRadio.isSelected()) credLayout.show(credCards, CARD_USER_PASS);
        else if (userCertRadio.isSelected()) credLayout.show(credCards, CARD_USER_CERT);
        else                                 credLayout.show(credCards, CARD_NONE);
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refreshList() {
        int sel = profileList != null ? profileList.getSelectedIndex() : 0;
        listModel.clear();
        store.getProfiles().forEach(listModel::addElement);
        if (profileList != null && sel < listModel.size())
            profileList.setSelectedIndex(sel);
    }

    private static JLabel label(String t)        { return new JLabel(t); }
    private static JLabel sectionLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setForeground(Color.DARK_GRAY);
        return l;
    }

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
    private static GridBagConstraints sectionCon(int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(10, 4, 2, 4); return c;
    }
    private static void addRow(JPanel p, String lbl, Component field,
                               int row, GridBagConstraints lc, GridBagConstraints fc) {
        lc.gridy = row; fc.gridy = row;
        p.add(new JLabel(lbl), lc);
        p.add(field, fc);
    }

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
}