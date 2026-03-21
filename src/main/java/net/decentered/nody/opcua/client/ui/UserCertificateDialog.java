package net.decentered.nody.opcua.client.ui;

import net.decentered.nody.opcua.client.security.UserCertificateManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Modal dialog for creating and exporting OPC UA user identity certificates.
 *
 * <h3>Sections</h3>
 * <ol>
 *   <li><b>Generate</b> – fill in subject fields (CN, O, C, validity), choose a
 *       destination PKCS#12 file and keystore password, then click
 *       <em>Generate Certificate</em>.  The result is shown in the details table.</li>
 *   <li><b>Export</b> – load any existing PKCS#12 file and export its public
 *       certificate as PEM to clipboard or file, ready for the server
 *       administrator to add to the server's user trust list.</li>
 * </ol>
 *
 * <p>Opened via <b>Security → Create / Export User Certificate…</b>.</p>
 */
public class UserCertificateDialog extends JDialog {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                    .withZone(ZoneId.systemDefault());

    private final UserCertificateManager manager = new UserCertificateManager();

    // ── Generate section ──────────────────────────────────────────────────────
    private final JTextField    cnField       = new JTextField(20);
    private final JTextField    orgField      = new JTextField(16);
    private final JTextField    countryField  = new JTextField(4);
    private final JSpinner      validitySpinner;
    private final JTextField    outputPathField = new JTextField(28);
    private final JPasswordField genPasswordField = new JPasswordField(14);
    private final JPasswordField genPasswordConfirm = new JPasswordField(14);
    private final JButton       generateButton = new JButton("Generate Certificate");

    // ── Export section ────────────────────────────────────────────────────────
    private final JTextField    exportPathField   = new JTextField(28);
    private final JPasswordField exportPassField  = new JPasswordField(14);
    private final JButton       exportClipButton  = new JButton("Copy PEM to Clipboard");
    private final JButton       exportFileButton  = new JButton("Save PEM…");

    // ── Details table ─────────────────────────────────────────────────────────
    private final DefaultTableModel detailsModel;
    private final JTable            detailsTable;

    public UserCertificateDialog(Frame owner) {
        super(owner, "Create / Export User Certificate", true);
        setSize(760, 680);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));

        validitySpinner = new JSpinner(new SpinnerNumberModel(5, 1, 30, 1));

        // ── Top: Generate + Export panels side by side ────────────────────────
        JPanel topRow = new JPanel(new GridLayout(1, 2, 10, 0));
        topRow.add(buildGeneratePanel());
        topRow.add(buildExportPanel());
        add(topRow, BorderLayout.NORTH);

        // ── Centre: certificate details ───────────────────────────────────────
        detailsModel = new DefaultTableModel(new String[]{"Field", "Value"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        detailsTable = new JTable(detailsModel);
        detailsTable.setFillsViewportHeight(true);
        detailsTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailsTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        detailsTable.getColumnModel().getColumn(0).setMaxWidth(210);
        detailsTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        detailsTable.setDefaultRenderer(Object.class,
                new javax.swing.table.DefaultTableCellRenderer() {
                    @Override public Component getTableCellRendererComponent(
                            JTable t, Object v, boolean sel, boolean focus, int row, int col) {
                        Component c = super.getTableCellRendererComponent(t, v, sel, focus, row, col);
                        if (!sel) c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(245, 247, 250));
                        return c;
                    }
                });

        JPanel detailsPanel = new JPanel(new BorderLayout());
        detailsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Certificate Details",
                TitledBorder.LEFT, TitledBorder.TOP));
        detailsPanel.add(new JScrollPane(detailsTable), BorderLayout.CENTER);
        add(detailsPanel, BorderLayout.CENTER);

        // ── Bottom: close button ──────────────────────────────────────────────
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(closeBtn);
        add(bottom, BorderLayout.SOUTH);

        // ── Wiring ────────────────────────────────────────────────────────────
        generateButton.addActionListener(e -> handleGenerate());
        exportClipButton.addActionListener(e -> handleExport(true));
        exportFileButton.addActionListener(e -> handleExport(false));
    }

    // ── Panel builders ────────────────────────────────────────────────────────

    private JPanel buildGeneratePanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Generate New User Certificate",
                TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints lc = labelConstraints();
        GridBagConstraints fc = fieldConstraints();

        int row = 0;
        cnField.setText(System.getProperty("user.name", ""));
        countryField.setText("DE");

        addRow(p, "Common Name (CN) *:", cnField, row++, lc, fc);
        addRow(p, "Organisation (O):",    orgField, row++, lc, fc);
        addRow(p, "Country (C):",         countryField, row++, lc, fc);

        JPanel validRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        validRow.add(validitySpinner);
        validRow.add(new JLabel("  years"));
        addRow(p, "Validity:",            validRow, row++, lc, fc);

        // Output path
        JPanel outRow = new JPanel(new BorderLayout(4, 0));
        outRow.add(outputPathField, BorderLayout.CENTER);
        JButton browseOut = new JButton("…");
        browseOut.setPreferredSize(new Dimension(28, 24));
        browseOut.addActionListener(e -> browseOutputFile());
        outRow.add(browseOut, BorderLayout.EAST);
        addRow(p, "Save to (*.p12) *:",   outRow, row++, lc, fc);

        addRow(p, "Keystore Password *:", genPasswordField, row++, lc, fc);
        addRow(p, "Confirm Password *:",  genPasswordConfirm, row++, lc, fc);

        // Generate button – full width
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 0; bc.gridy = row; bc.gridwidth = 2;
        bc.insets = new Insets(10, 4, 4, 4);
        bc.fill = GridBagConstraints.HORIZONTAL;
        generateButton.setFont(generateButton.getFont().deriveFont(Font.BOLD));
        p.add(generateButton, bc);

        return p;
    }

    private JPanel buildExportPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Export Existing Certificate as PEM",
                TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints lc = labelConstraints();
        GridBagConstraints fc = fieldConstraints();

        int row = 0;

        // Source path
        JPanel srcRow = new JPanel(new BorderLayout(4, 0));
        srcRow.add(exportPathField, BorderLayout.CENTER);
        JButton browseExport = new JButton("…");
        browseExport.setPreferredSize(new Dimension(28, 24));
        browseExport.addActionListener(e -> browseExportFile());
        srcRow.add(browseExport, BorderLayout.EAST);
        addRow(p, "PKCS#12 file *:", srcRow, row++, lc, fc);

        addRow(p, "Keystore Password:", exportPassField, row++, lc, fc);

        // Description label
        GridBagConstraints dc = new GridBagConstraints();
        dc.gridx = 0; dc.gridy = row++; dc.gridwidth = 2;
        dc.insets = new Insets(8, 4, 4, 4);
        dc.anchor = GridBagConstraints.WEST; dc.fill = GridBagConstraints.HORIZONTAL;
        JLabel hint = new JLabel(
                "<html><i>Give the exported PEM to the OPC UA server<br>" +
                        "admin to add it to the server's user trust list.</i></html>");
        hint.setForeground(Color.DARK_GRAY);
        p.add(hint, dc);

        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 0; bc.gridy = row; bc.gridwidth = 2;
        bc.insets = new Insets(4, 4, 4, 4); bc.fill = GridBagConstraints.HORIZONTAL;
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 6, 0));
        btnRow.add(exportClipButton);
        btnRow.add(exportFileButton);
        p.add(btnRow, bc);

        return p;
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    private void handleGenerate() {
        String cn      = cnField.getText().trim();
        String org     = orgField.getText().trim();
        String country = countryField.getText().trim().toUpperCase();
        int    years   = (Integer) validitySpinner.getValue();
        String outPath = outputPathField.getText().trim();

        char[] pw      = genPasswordField.getPassword();
        char[] pwConf  = genPasswordConfirm.getPassword();

        // ── Validate ──────────────────────────────────────────────────────────
        if (cn.isBlank()) {
            err("Please enter a Common Name (CN)."); clearPw(pw, pwConf); return; }
        if (outPath.isBlank()) {
            err("Please choose a destination file."); clearPw(pw, pwConf); return; }
        if (pw.length == 0) {
            err("Please enter a keystore password."); clearPw(pw, pwConf); return; }
        if (!Arrays.equals(pw, pwConf)) {
            err("Passwords do not match."); clearPw(pw, pwConf); return; }
        clearPw(pwConf);  // confirm copy no longer needed

        // ── Generate ──────────────────────────────────────────────────────────
        try {
            // pw is zeroed inside generate() after saving
            X509Certificate cert = manager.generate(
                    cn, org, country, years, Path.of(outPath), pw);
            clearPw(pw);

            // Auto-fill export section with the just-created file
            exportPathField.setText(outPath);

            showCertDetails(cert, outPath);
            JOptionPane.showMessageDialog(this,
                    "Certificate generated successfully.\n" + outPath,
                    "Done", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            clearPw(pw);
            err("Generation failed:\n" + ex.getMessage());
        }
    }

    private void handleExport(boolean toClipboard) {
        String srcPath = exportPathField.getText().trim();
        char[] pw      = exportPassField.getPassword();

        if (srcPath.isBlank()) {
            err("Please choose a PKCS#12 file to export."); clearPw(pw); return; }
        if (!Files.exists(Path.of(srcPath))) {
            err("File not found:\n" + srcPath); clearPw(pw); return; }

        try {
            if (toClipboard) {
                // pw zeroed inside exportPublicKeyPem
                Path tmpPem = Files.createTempFile("nody-user-cert-", ".pem");
                String pem  = manager.exportPublicKeyPem(Path.of(srcPath), pw, tmpPem);
                clearPw(pw);
                Files.deleteIfExists(tmpPem);
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(pem), null);
                JOptionPane.showMessageDialog(this,
                        "PEM certificate copied to clipboard.\n" +
                                "Give it to the OPC UA server administrator\n" +
                                "to add it to the server's user trust list.",
                        "Exported", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Save PEM Certificate");
                String defaultName = Path.of(srcPath).getFileName().toString()
                        .replaceAll("\\.(p12|pfx)$", "") + ".pem";
                fc.setSelectedFile(new File(defaultName));
                fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        "PEM certificate (*.pem)", "pem"));
                if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                    clearPw(pw); return;
                }
                Path pemPath = fc.getSelectedFile().toPath();
                // pw zeroed inside exportPublicKeyPem
                manager.exportPublicKeyPem(Path.of(srcPath), pw, pemPath);
                clearPw(pw);

                // Show the cert details after export
                X509Certificate cert = manager.readCertificate(
                        Path.of(srcPath), exportPassField.getPassword());
                showCertDetails(cert, srcPath);

                JOptionPane.showMessageDialog(this,
                        "PEM certificate saved to:\n" + pemPath,
                        "Saved", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            clearPw(pw);
            err("Export failed:\n" + ex.getMessage());
        }
    }

    // ── File browsers ─────────────────────────────────────────────────────────

    private void browseOutputFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save PKCS#12 User Certificate");
        fc.setSelectedFile(new File(
                System.getProperty("user.home") + File.separator + "user-identity.p12"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "PKCS#12 keystore (*.p12, *.pfx)", "p12", "pfx"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getAbsolutePath();
            if (!path.endsWith(".p12") && !path.endsWith(".pfx")) path += ".p12";
            outputPathField.setText(path);
        }
    }

    private void browseExportFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select PKCS#12 User Certificate");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "PKCS#12 keystore (*.p12, *.pfx)", "p12", "pfx"));
        if (!outputPathField.getText().isBlank())
            fc.setCurrentDirectory(new File(outputPathField.getText()).getParentFile());
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            exportPathField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    // ── Certificate details display ───────────────────────────────────────────

    private void showCertDetails(X509Certificate cert, String sourcePath) {
        detailsModel.setRowCount(0);
        addDetail("Subject",     cert.getSubjectX500Principal().getName());
        addDetail("Issuer",      cert.getIssuerX500Principal().getName());
        addDetail("Serial",      cert.getSerialNumber().toString());
        addDetail("Valid From",  FMT.format(cert.getNotBefore().toInstant()));
        addDetail("Valid Until", FMT.format(cert.getNotAfter().toInstant()));
        addDetail("Signature",   cert.getSigAlgName());
        addDetail("Key Size",    keyBits(cert) + " bit RSA");
        addDetail("SHA-256 Thumbprint", thumbprint(cert));
        addDetail("PKCS#12 file", sourcePath);
    }

    private void addDetail(String name, String value) {
        detailsModel.addRow(new Object[]{name, value != null ? value : "<null>"});
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void err(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static void clearPw(char[]... arrays) {
        for (char[] a : arrays) if (a != null) Arrays.fill(a, '\0');
    }

    private static String thumbprint(X509Certificate cert) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            return HexFormat.ofDelimiter(":").formatHex(digest).toUpperCase();
        } catch (Exception e) { return "<error>"; }
    }

    private static int keyBits(X509Certificate cert) {
        try {
            return ((java.security.interfaces.RSAPublicKey) cert.getPublicKey())
                    .getModulus().bitLength();
        } catch (Exception e) { return -1; }
    }

    // ── GridBag helpers ───────────────────────────────────────────────────────

    private static GridBagConstraints labelConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(3, 4, 3, 6); return c;
    }

    private static GridBagConstraints fieldConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        c.insets = new Insets(3, 0, 3, 4); return c;
    }

    private static void addRow(JPanel panel, String label, Component field,
                               int row, GridBagConstraints lc, GridBagConstraints fc) {
        lc.gridy = row; fc.gridy = row;
        panel.add(new JLabel(label), lc);
        panel.add(field, fc);
    }
}