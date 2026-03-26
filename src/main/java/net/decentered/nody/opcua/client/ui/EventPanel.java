package net.decentered.nody.opcua.client.ui;

import net.decentered.nody.opcua.client.model.EventEntry;
import net.decentered.nody.opcua.client.subscription.SubscriptionService;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Tab 3 – Event view.
 *
 * <h3>Top left: event nodes</h3>
 * Drop OPC UA nodes with the EventNotifier attribute set here. The server
 * will push events from those nodes to the table.
 *
 * <h3>Top right: subscription parameters</h3>
 * Publishing interval and an [Apply] button.
 *
 * <h3>Centre: event table</h3>
 * Columns: Time | Severity | Source | Message | (dynamic extra fields)
 */
public class EventPanel extends JPanel {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());

    // ── Event nodes list ──────────────────────────────────────────────────────
    private final DefaultListModel<NodeEntry> nodeListModel = new DefaultListModel<>();
    private final JList<NodeEntry>            nodeList      = new JList<>(nodeListModel);

    // ── Event table ───────────────────────────────────────────────────────────
    private static final String[] BASE_COLS = { "Time", "Severity", "Source Node", "Message" };
    private final List<EventEntry> events = new ArrayList<>();
    private final AbstractTableModel eventTableModel = new AbstractTableModel() {
        @Override public int    getRowCount()    { return events.size(); }
        @Override public int    getColumnCount() { return BASE_COLS.length; }
        @Override public String getColumnName(int c) { return BASE_COLS[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Object getValueAt(int r, int c) {
            EventEntry e = events.get(r);
            return switch (c) {
                case 0 -> FMT.format(e.getTime());
                case 1 -> e.getSeverity();
                case 2 -> e.getSourceNode();
                case 3 -> e.getMessage();
                default -> "";
            };
        }
    };

    // ── Widgets ───────────────────────────────────────────────────────────────
    private final JSpinner publishingIntervalSpinner =
            new JSpinner(new SpinnerNumberModel(1000.0, 100.0, 60000.0, 100.0));
    private final JTable eventTable = new JTable(eventTableModel);

    // ── Service ───────────────────────────────────────────────────────────────
    private SubscriptionService subscriptionService;

    // ── Construction ──────────────────────────────────────────────────────────

    public EventPanel() {
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        add(buildTopPanel(), BorderLayout.NORTH);
        add(buildEventTable(), BorderLayout.CENTER);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setSubscriptionService(SubscriptionService svc) {
        this.subscriptionService = svc;
    }

    /** Called from Milo callback thread – adds event and refreshes on EDT. */
    public void addEvent(EventEntry entry) {
        SwingUtilities.invokeLater(() -> {
            events.add(0, entry); // newest first
            eventTableModel.fireTableRowsInserted(0, 0);
        });
    }

    public void clear() {
        events.clear();
        eventTableModel.fireTableDataChanged();
        nodeListModel.clear();
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private JPanel buildTopPanel() {
        JPanel top = new JPanel(new BorderLayout(8, 0));

        // Left: event nodes drop list
        JPanel nodesPanel = new JPanel(new BorderLayout(0, 4));
        nodesPanel.setBorder(BorderFactory.createTitledBorder("Event Nodes  (drop here)"));
        nodesPanel.setPreferredSize(new Dimension(260, 120));

        nodeList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        nodesPanel.add(new JScrollPane(nodeList), BorderLayout.CENTER);

        JButton removeNodeBtn = new JButton("Remove");
        removeNodeBtn.addActionListener(e -> removeSelectedNode());
        JPanel nodeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        nodeButtons.add(removeNodeBtn);
        nodesPanel.add(nodeButtons, BorderLayout.SOUTH);

        // Enable drop onto the list
        nodeList.setTransferHandler(new NodeDropHandler());
        nodeList.setDropMode(DropMode.INSERT);

        // Right: subscription params
        JPanel params = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        params.setBorder(BorderFactory.createTitledBorder("Subscription Parameters"));
        params.add(new JLabel("Publishing Interval (ms):"));
        params.add(publishingIntervalSpinner);
        JButton applyBtn = new JButton("Apply");
        applyBtn.addActionListener(e -> applyParams());
        params.add(applyBtn);

        // Clear events button
        JButton clearBtn = new JButton("Clear Events");
        clearBtn.addActionListener(e -> {
            events.clear();
            eventTableModel.fireTableDataChanged();
        });
        params.add(Box.createHorizontalStrut(16));
        params.add(clearBtn);

        top.add(nodesPanel, BorderLayout.WEST);
        top.add(params,     BorderLayout.CENTER);
        return top;
    }

    private JPanel buildEventTable() {
        eventTable.setFillsViewportHeight(true);
        eventTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        eventTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        eventTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        eventTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        eventTable.getColumnModel().getColumn(2).setPreferredWidth(160);
        eventTable.getColumnModel().getColumn(3).setPreferredWidth(300);

        // Colour rows by severity: ≥700 red, ≥400 orange, else default
        eventTable.setDefaultRenderer(Object.class,
                new javax.swing.table.DefaultTableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(JTable t, Object val,
                                                                   boolean sel, boolean focus, int row, int col) {
                        super.getTableCellRendererComponent(t, val, sel, focus, row, col);
                        if (!sel && row < events.size()) {
                            int sev = events.get(row).getSeverity();
                            if      (sev >= 700) setBackground(new Color(255, 200, 200));
                            else if (sev >= 400) setBackground(new Color(255, 235, 180));
                            else                 setBackground(row % 2 == 0
                                        ? Color.WHITE : new Color(245, 247, 250));
                        }
                        return this;
                    }
                });

        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Events"));
        p.add(new JScrollPane(eventTable), BorderLayout.CENTER);
        return p;
    }

    private void applyParams() {
        if (subscriptionService == null) return;
        double interval = ((Number) publishingIntervalSpinner.getValue()).doubleValue();
        subscriptionService.applyEventSubscriptionParams(interval);
    }

    private void removeSelectedNode() {
        int idx = nodeList.getSelectedIndex();
        if (idx < 0) return;
        NodeEntry entry = nodeListModel.remove(idx);
        if (subscriptionService != null)
            subscriptionService.removeEventNode(entry.nodeId());
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private record NodeEntry(NodeId nodeId, String displayName) {
        @Override public String toString() { return displayName + "  [" + nodeId.toParseableString() + "]"; }
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
                for (int i = 0; i < nodeListModel.size(); i++) {
                    if (nodeListModel.get(i).nodeId().equals(nodeId)) return false;
                }

                String name = nodeId.toParseableString();
                if (t instanceof NodeIdTransferable nit) name = nit.getDisplayName();

                nodeListModel.addElement(new NodeEntry(nodeId, name));

                if (subscriptionService != null) subscriptionService.addEventNode(nodeId);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}