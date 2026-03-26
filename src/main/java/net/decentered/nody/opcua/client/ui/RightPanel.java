package net.decentered.nody.opcua.client.ui;

import net.decentered.nody.opcua.client.model.EventEntry;
import net.decentered.nody.opcua.client.model.MonitoredItemEntry;
import net.decentered.nody.opcua.client.model.NodeAttribute;
import net.decentered.nody.opcua.client.subscription.SubscriptionService;

import javax.swing.*;
import java.util.List;

/**
 * The right-hand panel shown next to the node tree.
 * Contains a {@link JTabbedPane} with three tabs:
 * <ol>
 *   <li><b>Node Attributes</b> – read-only attribute table (existing behaviour)</li>
 *   <li><b>Data Subscription</b> – configurable subscription with drag-and-drop items</li>
 *   <li><b>Event View</b> – event subscription with drag-and-drop event nodes</li>
 * </ol>
 */
public class RightPanel extends JPanel {

    private final AttributePanel    attributePanel    = new AttributePanel();
    private final SubscriptionPanel subscriptionPanel = new SubscriptionPanel();
    private final EventPanel        eventPanel        = new EventPanel();
    private final JTabbedPane       tabs;

    public RightPanel() {
        setLayout(new java.awt.BorderLayout());

        tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.addTab("Node Attributes",    new ImageIcon(), attributePanel,
                "Read-only attributes of the currently selected node");
        tabs.addTab("Data Subscription",  new ImageIcon(), subscriptionPanel,
                "Subscribe to data-change notifications from OPC UA nodes");
        tabs.addTab("Event View",         new ImageIcon(), eventPanel,
                "Subscribe to OPC UA events from event-notifier nodes");

        // Use plain text – clear the empty icons set above so headers look clean
        tabs.setIconAt(0, null);
        tabs.setIconAt(1, null);
        tabs.setIconAt(2, null);

        add(tabs);
    }

    // ── Wire subscription service ─────────────────────────────────────────────

    public void setSubscriptionService(SubscriptionService svc) {
        subscriptionPanel.setSubscriptionService(svc);
        eventPanel.setSubscriptionService(svc);
    }

    // ── AttributePanel delegation ─────────────────────────────────────────────

    public void showAttributes(String nodeIdStr, List<NodeAttribute> attributes) {
        attributePanel.showAttributes(nodeIdStr, attributes);
    }

    public void showLoading(String nodeIdStr) {
        attributePanel.showLoading(nodeIdStr);
    }

    public void clear() {
        attributePanel.clear();
        subscriptionPanel.clear();
        eventPanel.clear();
    }

    // ── Subscription callbacks (called on Milo thread → dispatched to EDT) ────

    public void onDataChange(MonitoredItemEntry entry) {
        subscriptionPanel.updateItem(entry);
    }

    public void onEvent(EventEntry entry) {
        eventPanel.addEvent(entry);
    }
}