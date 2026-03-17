package net.decentered.nody.opcua.client.ui;

import net.decentered.nody.opcua.client.model.NodeAttribute;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Right panel – shows the attributes of the currently selected OPC UA node
 * in a two-column table. Fully passive: data is pushed in via
 * {@link #showAttributes}.
 */
public class AttributePanel extends JPanel {

    private static final String[] COLUMN_NAMES = {"Attribute", "Value"};

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel titleLabel;

    public AttributePanel() {
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createTitledBorder("Node Attributes"));

        titleLabel = new JLabel("No node selected");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.ITALIC));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(titleLabel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(0).setMaxWidth(220);
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));

        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(245, 247, 250));
                }
                return c;
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void showAttributes(String nodeIdStr, List<NodeAttribute> attributes) {
        titleLabel.setText("Node: " + nodeIdStr);
        tableModel.setRowCount(0);
        for (NodeAttribute attr : attributes) {
            tableModel.addRow(new Object[]{attr.name(), attr.value()});
        }
    }

    public void showLoading(String nodeIdStr) {
        titleLabel.setText("Loading: " + nodeIdStr);
        tableModel.setRowCount(0);
        tableModel.addRow(new Object[]{"…", "Reading attributes…"});
    }

    public void clear() {
        titleLabel.setText("No node selected");
        tableModel.setRowCount(0);
    }
}