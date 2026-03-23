package net.decentered.nody.opcua.client.ui;

import net.decentered.nody.opcua.client.model.AuthMode;
import net.decentered.nody.opcua.client.model.ConnectionConfig;
import net.decentered.nody.opcua.client.model.ConnectionProfile;
import net.decentered.nody.opcua.client.settings.ConnectionProfileStore;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Slim top bar: a non-editable combo box of saved {@link ConnectionProfile}s,
 * an inline password field (shown only when the profile requires one),
 * a Connect / Disconnect button, and a status label.
 *
 * <p>Non-editable is intentional: typing a raw URL belongs in
 * {@link AdHocConnectionDialog} (Connections → Quick Connect…), which keeps
 * this bar simple and avoids the race conditions that plagued the editable
 * approach.</p>
 */
public class QuickConnectBar extends JPanel {

    private final ConnectionProfileStore     store;
    private final Consumer<ConnectionConfig> onConnect;
    private final Runnable                   onDisconnect;

    // ── Widgets ───────────────────────────────────────────────────────────────
    private final JComboBox<ConnectionProfile> profileCombo;
    private final JLabel                       authHintLabel;
    private final JPasswordField               passwordField;
    private final JButton                      connectButton;
    private final JLabel                       statusLabel;

    private boolean connected = false;

    // ── Construction ──────────────────────────────────────────────────────────

    public QuickConnectBar(ConnectionProfileStore store,
                           Consumer<ConnectionConfig> onConnect,
                           Runnable onDisconnect) {
        this.store        = store;
        this.onConnect    = onConnect;
        this.onDisconnect = onDisconnect;

        setLayout(new BorderLayout(8, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        // ── Left: label + combo ───────────────────────────────────────────────
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(new JLabel("Connection:"));
        profileCombo = new JComboBox<>();
        profileCombo.setPreferredSize(new Dimension(400, 26));
        profileCombo.setRenderer(new ProfileRenderer());
        left.add(profileCombo);

        // ── Centre: inline password (only when profile requires one) ──────────
        authHintLabel = new JLabel();
        authHintLabel.setForeground(Color.DARK_GRAY);
        passwordField = new JPasswordField(12);

        JPanel centre = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        centre.add(authHintLabel);
        centre.add(passwordField);

        // ── Right: button + status ────────────────────────────────────────────
        connectButton = new JButton("Connect");
        connectButton.setPreferredSize(new Dimension(120, 26));

        statusLabel = new JLabel("Disconnected");
        statusLabel.setForeground(Color.GRAY);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        right.add(connectButton);
        right.add(statusLabel);

        add(left,   BorderLayout.WEST);
        add(centre, BorderLayout.CENTER);
        add(right,  BorderLayout.EAST);

        // ── Wiring ────────────────────────────────────────────────────────────
        profileCombo.addActionListener(e -> syncPasswordArea());
        connectButton.addActionListener(e -> handleButton());

        // Populate combo – safe now that all fields are assigned
        reloadProfiles();
    }

    // ── Public API (called from EDT) ──────────────────────────────────────────

    /** Reloads the combo from the store (call after Connection Manager closes). */
    public void reloadProfiles() {
        ConnectionProfile current = (ConnectionProfile) profileCombo.getSelectedItem();

        profileCombo.removeAllItems();
        for (ConnectionProfile p : store.getProfiles()) profileCombo.addItem(p);

        // Restore previously selected profile by ID
        if (current != null) {
            for (int i = 0; i < profileCombo.getItemCount(); i++) {
                if (profileCombo.getItemAt(i).getId().equals(current.getId())) {
                    profileCombo.setSelectedIndex(i);
                    syncPasswordArea();
                    return;
                }
            }
        }

        // Fall back to the last saved selection
        int saved = store.getSelectedIndex();
        if (saved >= 0 && saved < profileCombo.getItemCount()) {
            profileCombo.setSelectedIndex(saved);
        }
        syncPasswordArea();
    }

    public void setConnecting() {
        connectButton.setEnabled(false);
        connectButton.setText("Connecting…");
        profileCombo.setEnabled(false);
        passwordField.setEnabled(false);
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
        profileCombo.setEnabled(true);
        passwordField.setEnabled(true);
        statusLabel.setText("Disconnected");
        statusLabel.setForeground(Color.GRAY);
    }

    public void setError(String message) {
        connected = false;
        connectButton.setEnabled(true);
        connectButton.setText("Connect");
        profileCombo.setEnabled(true);
        passwordField.setEnabled(true);
        statusLabel.setText("Error ✕");
        statusLabel.setForeground(Color.RED);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Show/hide the password field based on the selected profile's auth mode. */
    private void syncPasswordArea() {
        ConnectionProfile p = (ConnectionProfile) profileCombo.getSelectedItem();
        if (p == null) {
            authHintLabel.setVisible(false);
            passwordField.setVisible(false);
            return;
        }

        // Persist selection
        store.setSelectedIndex(profileCombo.getSelectedIndex());

        switch (p.getAuthMode()) {
            case USERNAME_PASSWORD -> {
                authHintLabel.setText("Password for '" + p.getUsername() + "':");
                authHintLabel.setVisible(true);
                passwordField.setText("");
                passwordField.setVisible(true);
            }
            case USER_CERTIFICATE -> {
                authHintLabel.setText("Keystore password:");
                authHintLabel.setVisible(true);
                passwordField.setText("");
                passwordField.setVisible(true);
            }
            default -> {
                authHintLabel.setVisible(false);
                passwordField.setVisible(false);
            }
        }
        revalidate();
        repaint();
    }

    private void handleButton() {
        if (!connected) {
            ConnectionProfile p = (ConnectionProfile) profileCombo.getSelectedItem();
            if (p == null) return;

            // Validate profile completeness before connecting
            if (p.getAuthMode() == AuthMode.USERNAME_PASSWORD && p.getUsername().isBlank()) {
                JOptionPane.showMessageDialog(this,
                        "This profile has no username configured.\n" +
                                "Edit it in Connections → Manage Connections.",
                        "Missing Username", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (p.getAuthMode() == AuthMode.USER_CERTIFICATE && p.getUserCertPath().isBlank()) {
                JOptionPane.showMessageDialog(this,
                        "This profile has no PKCS#12 certificate configured.\n" +
                                "Edit it in Connections → Manage Connections.",
                        "Missing Certificate", JOptionPane.WARNING_MESSAGE);
                return;
            }

            char[] pw     = passwordField.getPassword();
            char[] userPw = (p.getAuthMode() == AuthMode.USERNAME_PASSWORD) ? pw : new char[0];
            char[] certPw = (p.getAuthMode() == AuthMode.USER_CERTIFICATE)  ? pw : new char[0];
            Path certPath = p.getUserCertPath().isBlank()
                    ? null : Path.of(p.getUserCertPath());

            ConnectionConfig cfg = new ConnectionConfig(
                    p.getEndpointUrl(), p.getSecurityPolicy(), p.getSecurityMode(),
                    p.getAuthMode(), p.getUsername(), userPw, certPath, certPw);

            java.util.Arrays.fill(pw, '\0');
            setConnecting();
            onConnect.accept(cfg);

        } else {
            setDisconnecting();
            onDisconnect.run();
        }
    }

    // ── Renderer ──────────────────────────────────────────────────────────────

    /** Shows the profile name in the combo; URL shown in a smaller grey hint. */
    private static class ProfileRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ConnectionProfile p) {
                setText("<html><b>" + p.getName() + "</b>"
                        + "  <font color='gray'>" + p.getEndpointUrl() + "</font></html>");
            }
            return this;
        }
    }
}