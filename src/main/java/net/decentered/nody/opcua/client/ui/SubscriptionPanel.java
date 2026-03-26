package net.decentered.nody.opcua.client.ui;

import net.decentered.nody.opcua.client.model.MonitoredItemEntry;
import net.decentered.nody.opcua.client.subscription.SubscriptionService;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tab 2 – Data Subscription view.
 *
 * <h3>Top: subscription parameters</h3>
 * Publishing interval, max keep-alive count, max notifications per publish,
 * default sampling interval, and an [Apply] button.
 *
 * <h3>Centre: monitored items table</h3>
 * Columns: Display Name | Node ID | Sampling (ms) | Value | Timestamp | Status
 * Rows are added by dragging nodes from the {@link NodeTreePanel}.
 *
 * <h3>Drop target</h3>
 * The table accepts drops of {@link NodeIdTransferable#NODE_ID_FLAVOR}.
 */
public class SubscriptionPanel extends JPanel {

    // ── Table model ───────────────────────────────────────────────────────────
    private static final String[] COLS =
            { "Display Name", "Node ID", "Sampling (ms)", "Value", "Timestamp", "Status" };

    private final List<MonitoredItemEntry> items = new ArrayList<>();

    private final AbstractTableModel tableModel = new AbstractTableModel() {
        @Override public int    getRowCount()    { return items.size(); }
        @Override public int    getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }
        @Override public boolean isCellEditable(int r, int c) { return c == 2; } // sampling editable
        @Override public Object getValueAt(int r, int c) {
            MonitoredItemEntry e = items.get(r);
            return switch (c) {
                case 0 -> e.getDisplayName();
                case 1 -> e.getNodeId().toParseableString();
                case 2 -> e.getSamplingInterval();
                case 3 -> e.getValue();
                case 4 -> e.getTimestamp();
                case 5 -> e.getStatus();
                default -> "";
            };
        }
        @Override public void setValueAt(Object val, int r, int c) {
            if (c == 2) {
                try {
                    items.get(r).setSamplingInterval(Double.parseDouble(val.toString()));
                    fireTableCellUpdated(r, c);
                } catch (NumberFormatException ignored) {}
            }
        }
        @Override public Class<?> getColumnClass(int c) {
            return (c == 2) ? Double.class : String.class;
        }
    };

    // ── Widgets ───────────────────────────────────────────────────────────────
    private final JSpinner publishingIntervalSpinner =
            new JSpinner(new SpinnerNumberModel(1000.0, 100.0, 60000.0, 100.0));
    private final JSpinner keepAliveSpinner   =
            new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
    private final JSpinner maxNotifSpinner    =
            new JSpinner(new SpinnerNumberModel(100, 1, 10000, 10));
    private final JSpinner defaultSamplingSpinner =
            new JSpinner(new SpinnerNumberModel(1000.0, 0.0, 60000.0, 100.0));

    private final JTable table = new JTable(tableModel);
    private final JLabel dropHint;

    // ── Callback ──────────────────────────────────────────────────────────────
    private SubscriptionService subscriptionService;

    // ── Construction ──────────────────────────────────────────────────────────

    public SubscriptionPanel() {
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        add(buildParamsPanel(), BorderLayout.NORTH);

        // Table
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(160);
        table.getColumnModel().getColumn(4).setPreferredWidth(150);
        table.getColumnModel().getColumn(5).setPreferredWidth(90);
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        dropHint = new JLabel(
                "⬇  Drag nodes from the Address Space tree to monitor them",
                JLabel.CENTER);
        dropHint.setForeground(Color.GRAY);
        dropHint.setFont(dropHint.getFont().deriveFont(Font.ITALIC, 12f));

        JPanel tableWrapper = new JPanel(new BorderLayout());
        tableWrapper.add(new JScrollPane(table), BorderLayout.CENTER);
        tableWrapper.add(dropHint, BorderLayout.SOUTH);
        add(tableWrapper, BorderLayout.CENTER);

        // Remove button
        JButton removeBtn = new JButton("Remove Selected");
        removeBtn.addActionListener(e -> removeSelected());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(removeBtn);
        add(south, BorderLayout.SOUTH);

        // Drop target on the table
        table.setTransferHandler(new NodeDropHandler());
        table.setDropMode(DropMode.INSERT_ROWS);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setSubscriptionService(SubscriptionService svc) {
        this.subscriptionService = svc;
    }

    /** Called from Milo callback thread → updates matching row, then refreshes on EDT. */
    public void updateItem(MonitoredItemEntry entry) {
        SwingUtilities.invokeLater(() -> {
            int idx = items.indexOf(entry);
            if (idx >= 0) tableModel.fireTableRowsUpdated(idx, idx);
        });
    }

    public void clear() {
        items.clear();
        tableModel.fireTableDataChanged();
    }

    // ── Build params panel ────────────────────────────────────────────────────

    private JPanel buildParamsPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(BorderFactory.createTitledBorder("Subscription Parameters"));

        p.add(new JLabel("Publishing Interval (ms):"));
        p.add(publishingIntervalSpinner);

        p.add(new JLabel("  Keep-Alive Count:"));
        p.add(keepAliveSpinner);

        p.add(new JLabel("  Max Notifications:"));
        p.add(maxNotifSpinner);

        p.add(new JLabel("  Default Sampling (ms):"));
        p.add(defaultSamplingSpinner);

        JButton applyBtn = new JButton("Apply");
        applyBtn.setToolTipText("Create/recreate the subscription with these parameters");
        applyBtn.addActionListener(e -> applyParams());
        p.add(applyBtn);

        return p;
    }

    private void applyParams() {
        if (subscriptionService == null) return;
        double pubInterval = ((Number) publishingIntervalSpinner.getValue()).doubleValue();
        int    keepAlive   = ((Number) keepAliveSpinner.getValue()).intValue();
        int    maxNotif    = ((Number) maxNotifSpinner.getValue()).intValue();
        subscriptionService.applyDataSubscriptionParams(pubInterval, keepAlive, maxNotif);
    }

    private void removeSelected() {
        int[] rows = table.getSelectedRows();
        for (int i = rows.length - 1; i >= 0; i--) {
            MonitoredItemEntry entry = items.remove(rows[i]);
            if (subscriptionService != null) subscriptionService.removeDataItem(entry);
        }
        tableModel.fireTableDataChanged();
    }

    // ── Drop handler ──────────────────────────────────────────────────────────

    private class NodeDropHandler extends TransferHandler {

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(NodeIdTransferable.NODE_ID_FLAVOR);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                Transferable t = support.getTransferable();
                NodeId nodeId  = (NodeId) t.getTransferData(NodeIdTransferable.NODE_ID_FLAVOR);

                // Avoid duplicates
                boolean exists = items.stream()
                        .anyMatch(e -> e.getNodeId().equals(nodeId));
                if (exists) return false;

                // Get display name from the transferable
                String name = nodeId.toParseableString();
                if (t instanceof NodeIdTransferable nit) name = nit.getDisplayName();

                double sampling = ((Number) defaultSamplingSpinner.getValue()).doubleValue();
                MonitoredItemEntry entry = new MonitoredItemEntry(nodeId, name, sampling);
                items.add(entry);
                tableModel.fireTableRowsInserted(items.size() - 1, items.size() - 1);

                if (subscriptionService != null) subscriptionService.addDataItem(entry);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}