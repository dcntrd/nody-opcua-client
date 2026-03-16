package net.decentered.nody.opcua.client.ui;

import net.decentered.nody.opcua.client.security.CertificateManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Modal dialog that displays the active self-signed client certificate's
 * details and provides an "Export PEM" action so the user can hand the
 * certificate to an OPC UA server administrator for trust-list registration.
 *
 * <p>Open this dialog via <b>Help → Certificate Info…</b> in the menu bar.</p>
 */
public class CertificateInfoDialog extends JDialog {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                    .withZone(ZoneId.systemDefault());

    public CertificateInfoDialog(Frame owner, CertificateManager certManager) {
        super(owner, "Client Application Certificate", true);
        setSize(680, 520);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── Try to load certificate ───────────────────────────────────────────
        X509Certificate cert = null;
        String loadError = null;
        try {
            certManager.getOrCreate();
            cert = certManager.getCertificate();
        } catch (Exception ex) {
            loadError = ex.getMessage();
        }

        if (cert == null) {
            add(new JLabel("Could not load certificate: " + loadError,
                    JLabel.CENTER), BorderLayout.CENTER);
            addCloseButton();
            return;
        }

        final X509Certificate finalCert = cert;

        // ── Attribute table ───────────────────────────────────────────────────
        DefaultTableModel model = new DefaultTableModel(new String[]{"Field", "Value"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        addRow(model, "Subject",     cert.getSubjectX500Principal().getName());
        addRow(model, "Issuer",      cert.getIssuerX500Principal().getName());
        addRow(model, "Serial",      cert.getSerialNumber().toString());
        addRow(model, "Valid From",  FMT.format(cert.getNotBefore().toInstant()));
        addRow(model, "Valid Until", FMT.format(cert.getNotAfter().toInstant()));
        addRow(model, "Signature",   cert.getSigAlgName());
        addRow(model, "Key Size",    keyBits(cert) + " bit");
        addRow(model, "Thumbprint (SHA-256)", thumbprint(cert));
        addRow(model, "Application URI", CertificateManager.APPLICATION_URI);
        addRow(model, "Keystore",    certManager.getKeystorePath().toString());
        addRow(model, "Trusted dir", CertificateManager.TRUSTED_DIR.toString());
        addRow(model, "Rejected dir",CertificateManager.REJECTED_DIR.toString());

        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(0).setMaxWidth(230);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                                                           boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                if (!sel) c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(245, 247, 250));
                return c;
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        // ── Button row ────────────────────────────────────────────────────────
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        JButton exportPem = new JButton("Export PEM to clipboard");
        exportPem.setToolTipText("Copy the certificate in PEM format – paste into the server's trust list");
        exportPem.addActionListener(e -> {
            try {
                String pem = toPem(finalCert);
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(pem), null);
                JOptionPane.showMessageDialog(this,
                        "PEM certificate copied to clipboard.\n" +
                                "Give it to the OPC UA server administrator\n" +
                                "to add it to the server's trust list.",
                        "Exported", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton savePem = new JButton("Save PEM…");
        savePem.setToolTipText("Save the certificate as a .pem file");
        savePem.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new java.io.File("nody-opcua-client.pem"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    Files.writeString(fc.getSelectedFile().toPath(), toPem(finalCert));
                    JOptionPane.showMessageDialog(this,
                            "Saved to " + fc.getSelectedFile().getAbsolutePath(),
                            "Saved", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JButton regenerate = new JButton("Regenerate…");
        regenerate.setForeground(new Color(160, 0, 0));
        regenerate.setToolTipText(
                "Delete the current certificate and generate a new one.\n" +
                        "You will need to re-register it with every server.");
        regenerate.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "This will delete the existing certificate and generate a new one.\n" +
                            "You will need to re-register the new certificate with all OPC UA servers.\n\n" +
                            "Are you sure?",
                    "Regenerate Certificate", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                try {
                    Files.deleteIfExists(certManager.getKeystorePath());
                    certManager.getOrCreate();
                    JOptionPane.showMessageDialog(this,
                            "New certificate generated.\nRestart the application to use it.",
                            "Done", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Regeneration failed: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());

        buttons.add(exportPem);
        buttons.add(savePem);
        buttons.add(regenerate);
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);
    }

    // Helpers

    private void addCloseButton() {
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        p.add(close);
        add(p, BorderLayout.SOUTH);
    }

    private static void addRow(DefaultTableModel m, String field, String value) {
        m.addRow(new Object[]{field, value != null ? value : "<null>"});
    }

    private static String thumbprint(X509Certificate cert) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            return HexFormat.ofDelimiter(":").formatHex(digest).toUpperCase();
        } catch (Exception e) {
            return "<error>";
        }
    }

    private static int keyBits(X509Certificate cert) {
        try {
            java.security.interfaces.RSAPublicKey rsa =
                    (java.security.interfaces.RSAPublicKey) cert.getPublicKey();
            return rsa.getModulus().bitLength();
        } catch (Exception e) {
            return -1;
        }
    }

    private static String toPem(X509Certificate cert) throws Exception {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(cert.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + b64 + "\n-----END CERTIFICATE-----\n";
    }
}