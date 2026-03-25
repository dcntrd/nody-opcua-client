package net.decentered.nody.opcua.client.ui;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

import javax.swing.*;
import java.awt.*;
import java.util.function.BiConsumer;

/**
 * Modal dialog for writing a new value to an OPC UA variable node.
 *
 * <p>The user enters the new value as a string.  The dialog attempts to
 * parse the input into the most appropriate Java type by trying numeric
 * conversions in order (Boolean → Integer → Long → Double → String).</p>
 *
 * <p>The callback {@code onWrite} receives the {@link NodeId} and the
 * parsed {@link Variant}; it is responsible for calling the service.</p>
 */
public class WriteValueDialog extends JDialog {

    private final JTextField valueField;
    private final JLabel     typeHintLabel;
    private final BiConsumer<NodeId, Variant> onWrite;
    private final NodeId nodeId;

    /**
     * @param owner        parent frame
     * @param nodeId       the variable node to write
     * @param currentValue the current value as a string (pre-fills the field)
     * @param dataType     the node's DataType name (shown as hint)
     * @param onWrite      called with (nodeId, variant) when the user confirms
     */
    public WriteValueDialog(Frame owner,
                            NodeId nodeId,
                            String currentValue,
                            String dataType,
                            BiConsumer<NodeId, Variant> onWrite) {
        super(owner, "Write Value", true);
        this.nodeId  = nodeId;
        this.onWrite = onWrite;

        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // ── Form ──────────────────────────────────────────────────────────────
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 8, 16));

        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.anchor = GridBagConstraints.EAST;
        lc.insets = new Insets(4, 0, 4, 10);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1; fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0; fc.insets = new Insets(4, 0, 4, 0);

        // Node ID (read-only)
        lc.gridy = 0; fc.gridy = 0;
        form.add(new JLabel("Node:"), lc);
        JLabel nodeLabel = new JLabel(nodeId.toParseableString());
        nodeLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        nodeLabel.setForeground(Color.DARK_GRAY);
        form.add(nodeLabel, fc);

        // Data type hint
        lc.gridy = 1; fc.gridy = 1;
        form.add(new JLabel("Data Type:"), lc);
        typeHintLabel = new JLabel(dataType.isBlank() ? "<unknown>" : dataType);
        typeHintLabel.setForeground(Color.DARK_GRAY);
        form.add(typeHintLabel, fc);

        // Value input
        lc.gridy = 2; fc.gridy = 2;
        form.add(new JLabel("New Value:"), lc);
        valueField = new JTextField(currentValue != null ? currentValue : "", 26);
        valueField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        valueField.selectAll();
        form.add(valueField, fc);

        // Parse hint
        GridBagConstraints hc = new GridBagConstraints();
        hc.gridx = 1; hc.gridy = 3; hc.anchor = GridBagConstraints.WEST;
        hc.insets = new Insets(0, 0, 4, 0);
        JLabel hint = new JLabel(
                "<html><font color='gray' size='-1'>" +
                        "Parsed as: Boolean → Integer → Long → Double → String</font></html>");
        form.add(hint, hc);

        // ── Buttons ───────────────────────────────────────────────────────────
        JButton writeBtn  = new JButton("Write");
        JButton cancelBtn = new JButton("Cancel");
        writeBtn.setFont(writeBtn.getFont().deriveFont(Font.BOLD));
        getRootPane().setDefaultButton(writeBtn);

        writeBtn.addActionListener(e  -> handleWrite());
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        buttons.add(cancelBtn);
        buttons.add(writeBtn);

        setLayout(new BorderLayout());
        add(form,    BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(420, getHeight()));
        setLocationRelativeTo(owner);
        valueField.requestFocusInWindow();
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    private void handleWrite() {
        String raw = valueField.getText();
        Variant variant = parseVariant(raw);
        dispose();
        onWrite.accept(nodeId, variant);
    }

    /**
     * Attempts to parse the input string into the most appropriate type.
     * Order: Boolean → Integer → Long → Double → String.
     */
    private static Variant parseVariant(String raw) {
        if (raw == null) return new Variant(null);

        // Boolean
        if (raw.equalsIgnoreCase("true"))  return new Variant(Boolean.TRUE);
        if (raw.equalsIgnoreCase("false")) return new Variant(Boolean.FALSE);

        // Integer (32-bit)
        try { return new Variant(Integer.parseInt(raw)); }
        catch (NumberFormatException ignored) { }

        // Long (64-bit)
        try { return new Variant(Long.parseLong(raw)); }
        catch (NumberFormatException ignored) { }

        // Double (floating point)
        try { return new Variant(Double.parseDouble(raw)); }
        catch (NumberFormatException ignored) { }

        // Fallback: String
        return new Variant(raw);
    }
}