package net.decentered.nody.opcua.client.ui;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * About dialog for Nody OpcUa Client.
 *
 * <p>Layout: the logo PNG sits on the left; application name, version,
 * description and a clickable GitHub link appear on the right.</p>
 *
 * <p>Opened via <b>Help → About…</b>.</p>
 */
public class AboutDialog extends JDialog {

    private static final String APP_NAME    = "Nody OpcUa Client";
    /**
     * Reads {@code Implementation-Version} from {@code META-INF/MANIFEST.MF}
     * inside the running JAR.  Falls back to {@code "dev"} when running
     * from an IDE or exploded classpath where no manifest entry is present.
     */
    private static final String VERSION = readVersion();

    private static String readVersion() {
        // Primary: Package metadata written by maven-assembly-plugin
        Package pkg = AboutDialog.class.getPackage();
        if (pkg != null) {
            String v = pkg.getImplementationVersion();
            if (v != null && !v.isBlank()) return v;
        }
        // Fallback: open MANIFEST.MF explicitly (handles edge cases in some JVMs)
        try (var in = AboutDialog.class.getResourceAsStream(
                "/META-INF/MANIFEST.MF")) {
            if (in != null) {
                Manifest mf = new Manifest(in);
                String v = mf.getMainAttributes()
                        .getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                if (v != null && !v.isBlank()) return v;
            }
        } catch (Exception ignored) { }
        return "dev";
    }
    private static final String DESCRIPTION =
            "A desktop OPC UA client built with Eclipse Milo and Java Swing.";
    private static final String GITHUB_URL  =
            "https://github.com/dcntrd/nody-opcua-client";
    private static final String JAVA_VER    =
            System.getProperty("java.version", "unknown");
    private static final String MILO_VER    = "1.1.1";

    public AboutDialog(Frame owner) {
        super(owner, "About " + APP_NAME, true);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(20, 0));
        content.setBorder(BorderFactory.createEmptyBorder(24, 24, 20, 28));
        content.setBackground(Color.WHITE);

        // ── Left: logo ────────────────────────────────────────────────────────
        content.add(buildLogoPanel(), BorderLayout.WEST);

        // ── Right: text ───────────────────────────────────────────────────────
        content.add(buildTextPanel(), BorderLayout.CENTER);

        // ── Bottom: close button ──────────────────────────────────────────────
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottom.setBackground(Color.WHITE);
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 24, 16, 28));
        bottom.add(closeBtn);

        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        getContentPane().setBackground(Color.WHITE);
        add(content, BorderLayout.CENTER);
        add(bottom,  BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    // ── Logo panel ────────────────────────────────────────────────────────────

    private JPanel buildLogoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setOpaque(true);

        ImageIcon icon = loadLogo();
        JLabel logo = (icon != null)
                ? new JLabel(icon)
                : new JLabel("[ logo ]", JLabel.CENTER);
        logo.setBackground(Color.WHITE);
        logo.setOpaque(true);
        panel.add(logo, BorderLayout.CENTER);
        return panel;
    }

    private static ImageIcon loadLogo() {
        try {
            var url = AboutDialog.class.getResource("/images/nody-logo.png");
            if (url == null) {
                return null;
            }
            ImageIcon raw = new ImageIcon(url);
            // Scale to 109×128 for crisp display at standard DPI
            Image scaled = raw.getImage().getScaledInstance(109, 128, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Text panel ────────────────────────────────────────────────────────────

    private JPanel buildTextPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setOpaque(true);

        panel.add(Box.createVerticalStrut(8));

        // App name
        JLabel nameLabel = new JLabel(APP_NAME);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 22f));
        nameLabel.setForeground(new Color(18, 30, 48));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(nameLabel);

        panel.add(Box.createVerticalStrut(4));

        // Version
        JLabel versionLabel = new JLabel("Version " + VERSION);
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 13f));
        versionLabel.setForeground(Color.GRAY);
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(versionLabel);

        panel.add(Box.createVerticalStrut(14));

        // Description
        JLabel descLabel = new JLabel(
                "<html><body style='width:220px'>" + DESCRIPTION + "</body></html>");
        descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN, 13f));
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(descLabel);

        panel.add(Box.createVerticalStrut(16));

        // Separator
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(sep);

        panel.add(Box.createVerticalStrut(12));

        // Tech details
        panel.add(infoRow("Built with",  "Eclipse Milo " + MILO_VER + "  ·  Java " + JAVA_VER));
        panel.add(Box.createVerticalStrut(6));
        panel.add(infoRow("License",     "MIT License"));

        panel.add(Box.createVerticalStrut(12));

        // GitHub link
        JLabel linkTitle = new JLabel("Source Code");
        linkTitle.setFont(linkTitle.getFont().deriveFont(Font.BOLD, 12f));
        linkTitle.setForeground(Color.DARK_GRAY);
        linkTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(linkTitle);

        panel.add(Box.createVerticalStrut(4));
        panel.add(buildHyperlinkPane());

        panel.add(Box.createVerticalGlue());

        return panel;
    }

    /** Builds a "Label: value" row. */
    private static JPanel infoRow(String label, String value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setBackground(Color.WHITE);
        row.setOpaque(true);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel(label + ": ");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12f));
        lbl.setForeground(Color.DARK_GRAY);

        JLabel val = new JLabel(value);
        val.setFont(val.getFont().deriveFont(Font.PLAIN, 12f));

        row.add(lbl);
        row.add(val);
        return row;
    }

    /**
     * A {@link JEditorPane} rendered as HTML so the GitHub URL appears as a
     * real clickable hyperlink using the system browser.
     */
    private JComponent buildHyperlinkPane() {
        String html = "<html><body style='font-family:sans-serif; font-size:12pt; margin:0;'>"
                + "<a href='" + GITHUB_URL + "'>" + GITHUB_URL + "</a>"
                + "</body></html>";

        JEditorPane pane = new JEditorPane("text/html", html);
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(null);
        pane.setBackground(Color.WHITE);
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Inherit the dialog's font so the link matches surrounding text
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openBrowser(e.getURL().toExternalForm());
            }
        });

        return pane;
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                // Fallback: copy to clipboard and inform the user
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(url), null);
                JOptionPane.showMessageDialog(null,
                        "Your system does not support opening a browser automatically.\n" +
                                "The URL has been copied to your clipboard:\n" + url,
                        "Open Link", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException | URISyntaxException ex) {
            JOptionPane.showMessageDialog(null,
                    "Could not open browser: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
