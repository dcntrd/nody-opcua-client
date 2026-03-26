package net.decentered.nody.opcua.client.subscription;

import net.decentered.nody.opcua.client.model.EventEntry;
import net.decentered.nody.opcua.client.model.MonitoredItemEntry;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.subscriptions.*;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.structured.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * Manages two OPC UA {@link OpcUaSubscription}s – one for data changes, one
 * for events – on top of a connected {@link OpcUaClient}.
 *
 * <p>All callbacks are invoked on Milo's subscription notification thread;
 * callers (the UI panels) must dispatch to the EDT themselves.</p>
 */
public class SubscriptionService {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionService.class);

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());

    // ── Event select-clauses (the fields we request from the server) ──────────
    public static final SimpleAttributeOperand[] EVENT_FIELDS = {
            eventField("EventId"),
            eventField("EventType"),
            eventField("SourceName"),
            eventField("SourceNode"),
            eventField("Time"),
            eventField("Severity"),
            eventField("Message"),
    };
    public static final String[] EVENT_FIELD_NAMES =
            { "EventId", "EventType", "SourceName", "SourceNode", "Time", "Severity", "Message" };

    // ── Subscriptions ─────────────────────────────────────────────────────────
    private volatile OpcUaSubscription dataSubscription;
    private volatile OpcUaSubscription eventSubscription;

    /** nodeId-string → monitored item handle, for removal */
    private final Map<String, OpcUaMonitoredItem> dataHandles  = new ConcurrentHashMap<>();
    /** nodeId-string → monitored item handle, for removal */
    private final Map<String, OpcUaMonitoredItem> eventHandles = new ConcurrentHashMap<>();

    // ── Callbacks ─────────────────────────────────────────────────────────────
    /** Notified when a data-change value arrives: (entry, newValue). */
    private Consumer<MonitoredItemEntry>   onDataChange;
    /** Notified when an event arrives. */
    private Consumer<EventEntry>           onEvent;
    /** Notified when an error occurs: (operation, cause). */
    private BiConsumer<String, Throwable>  onError;

    // ── Active client ─────────────────────────────────────────────────────────
    private volatile OpcUaClient client;

    // ── Tracked items (for the UI) ────────────────────────────────────────────
    private final List<MonitoredItemEntry> dataItems  = new ArrayList<>();
    private final List<NodeId>             eventNodes = new ArrayList<>();

    public SubscriptionService() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void setCallbacks(Consumer<MonitoredItemEntry> onDataChange,
                             Consumer<EventEntry>          onEvent,
                             BiConsumer<String, Throwable> onError) {
        this.onDataChange = onDataChange;
        this.onEvent      = onEvent;
        this.onError      = onError;
    }

    /** Call when the OPC UA session has been established. */
    public void onConnected(OpcUaClient c) {
        this.client = c;
    }

    /** Call when disconnecting – tears down both subscriptions. */
    public void onDisconnected() {
        tearDown();
        client = null;
    }

    // ── Data subscription ─────────────────────────────────────────────────────

    /**
     * Creates (or recreates) the data subscription with the given parameters
     * and re-subscribes all existing data items.
     */
    public void applyDataSubscriptionParams(double publishingIntervalMs,
                                            int    maxKeepAliveCount,
                                            int    maxNotificationsPerPublish) {
        OpcUaClient c = client;
        if (c == null) return;

        executor(() -> {
            try {
                if (dataSubscription != null) {
                    dataSubscription.delete();
                    dataSubscription = null;
                }
                dataSubscription = new OpcUaSubscription(c);
                dataSubscription.setMaxKeepAliveCount(uint(maxKeepAliveCount));
                dataSubscription.setMaxNotificationsPerPublish(uint(maxNotificationsPerPublish));

                dataSubscription.create();
                dataSubscription.setPublishingInterval(publishingIntervalMs);
                LOG.info("Data subscription created: id={}, interval={}ms",
                        dataSubscription.getSubscriptionId(), dataSubscription.getRevisedPublishingInterval());

                // Re-subscribe all tracked items
                List<MonitoredItemEntry> items;
                synchronized (dataItems) { items = new ArrayList<>(dataItems); }
                for (MonitoredItemEntry entry : items) {
                    subscribeDataItem(entry, c);
                }
            } catch (Exception e) {
                LOG.error("applyDataSubscriptionParams failed", e);
                if (onError != null) onError.accept("dataSubscription", e);
            }
        });


//        executor(() -> {
//            try {
//                if (dataSubscription != null) {
//                    c.getSubscriptionManager().deleteSubscription(
//                            dataSubscription.getSubscriptionId()).get();
//                }
//                dataSubscription = c.getSubscriptionManager()
//                        .createSubscription(publishingIntervalMs).get();
//                LOG.info("Data subscription created: id={}, interval={}ms",
//                        dataSubscription.getSubscriptionId(), publishingIntervalMs);
//
//                // Re-subscribe all tracked items
//                List<MonitoredItemEntry> items;
//                synchronized (dataItems) { items = new ArrayList<>(dataItems); }
//                for (MonitoredItemEntry entry : items) {
//                    subscribeDataItem(entry, c);
//                }
//            } catch (Exception e) {
//                LOG.error("applyDataSubscriptionParams failed", e);
//                if (onError != null) onError.accept("dataSubscription", e);
//            }
//        });
    }

    /**
     * Adds a new monitored data item (or re-subscribes if already present).
     */
    public void addDataItem(MonitoredItemEntry entry) {
        synchronized (dataItems) {
            boolean exists = dataItems.stream()
                    .anyMatch(e -> e.getNodeId().equals(entry.getNodeId()));
            if (!exists) dataItems.add(entry);
        }
        OpcUaClient c = client;
        if (c == null || dataSubscription == null) return;
        executor(() -> subscribeDataItem(entry, c));
    }

    /** Removes a monitored data item. */
    public void removeDataItem(MonitoredItemEntry entry) {
        synchronized (dataItems) { dataItems.remove(entry); }

        OpcUaMonitoredItem opcUaMonitoredItem = dataHandles.remove(entry.getNodeId().toParseableString());
        OpcUaClient c = client;
        if (c == null || dataSubscription == null || opcUaMonitoredItem == null) return;
        executor(() -> {
            try {
                dataSubscription.removeMonitoredItem(opcUaMonitoredItem);
                dataSubscription.synchronizeMonitoredItems();
            } catch (Exception e) {
                LOG.warn("removeDataItem failed", e);
            }
        });
    }

    // ── Event subscription ────────────────────────────────────────────────────

    public void applyEventSubscriptionParams(double publishingIntervalMs) {
        OpcUaClient c = client;
        if (c == null) return;

        executor(() -> {
            try {
                if (eventSubscription != null) {
                    eventSubscription.delete();
                    eventSubscription = null;
                }
                eventSubscription= new OpcUaSubscription(c);
                eventSubscription.create();
                eventSubscription.setPublishingInterval(publishingIntervalMs);
                LOG.info("Event subscription created: id={}",
                        eventSubscription.getSubscriptionId());

                List<NodeId> nodes;
                synchronized (eventNodes) { nodes = new ArrayList<>(eventNodes); }
                for (NodeId nodeId : nodes) subscribeEventNode(nodeId, c);
            } catch (Exception e) {
                LOG.error("applyEventSubscriptionParams failed", e);
                if (onError != null) onError.accept("eventSubscription", e);
            }
        });
    }

    public void addEventNode(NodeId nodeId) {
        synchronized (eventNodes) {
            if (!eventNodes.contains(nodeId)) eventNodes.add(nodeId);
        }
        OpcUaClient c = client;
        if (c == null || eventSubscription == null) return;
        executor(() -> subscribeEventNode(nodeId, c));
    }

    public void removeEventNode(NodeId nodeId) {
        synchronized (eventNodes) { eventNodes.remove(nodeId); }
        OpcUaMonitoredItem opcUaMonitoredItem = eventHandles.remove(nodeId.toParseableString());
        OpcUaClient c = client;
        if (c == null || eventSubscription == null || opcUaMonitoredItem == null) return;
        executor(() -> {
            try {
                eventSubscription.removeMonitoredItem(opcUaMonitoredItem);
                eventSubscription.synchronizeMonitoredItems();
            } catch (Exception e) {
                LOG.warn("removeEventNode failed", e);
            }
        });
    }

    // ── Getters for UI restoration ────────────────────────────────────────────

    public List<MonitoredItemEntry> getDataItems() {
        synchronized (dataItems) { return new ArrayList<>(dataItems); }
    }
    public List<NodeId> getEventNodes() {
        synchronized (eventNodes) { return new ArrayList<>(eventNodes); }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void subscribeDataItem(MonitoredItemEntry entry, OpcUaClient c) {

        try {
            if (dataSubscription == null) return;

            var monitoredItem = OpcUaMonitoredItem.newDataItem(entry.getNodeId());
            monitoredItem.setSamplingInterval(entry.getSamplingInterval());
            monitoredItem.setQueueSize(uint(10));
            monitoredItem.setDiscardOldest(true);

            // Set a listener for data changes at the monitored item level.
            monitoredItem.setDataValueListener(
                    (item, value) ->
                    {
                        LOG.info(
                                "monitoredItem onDataReceived: nodeId={}, value={}",
                                item.getReadValueId().getNodeId(),
                                value.value());

                        entry.setValue(valueStr(value));
                        entry.setTimestamp(timeStr(value));
                        entry.setStatus(value.getStatusCode().isGood()
                                        ? "Good" : value.getStatusCode().toString());
                                if (onDataChange != null) onDataChange.accept(entry);                    }
            );

            // Add the MonitoredItem to the Subscription
            dataSubscription.addMonitoredItem(monitoredItem);

            // Synchronize the MonitoredItems with the server.
            // This will create, modify, and delete items as necessary.
            try {
                dataSubscription.synchronizeMonitoredItems();
                dataHandles.put(entry.getNodeId().toParseableString(),monitoredItem);
            } catch (MonitoredItemSynchronizationException e) {
                e.getCreateResults()
                        .forEach(
                                result -> {
                                        LOG.warn(
                                                "failed to create item: nodeId={}, serviceResult={}, operationResult={}",
                                                result.monitoredItem().getReadValueId().getNodeId(),
                                                result.serviceResult(),
                                                result.operationResult());

                                        if(result.isGood()) {
                                            dataHandles.put(entry.getNodeId().toParseableString(),
                                                    monitoredItem);
                                            entry.setStatus("Subscribed");
                                        } else {
                                            entry.setStatus("Error: " + result.serviceResult());
                                        }
                                    if (onDataChange != null) onDataChange.accept(entry);
                                }
                        );
            }

        } catch (Exception e) {
            LOG.error("subscribeDataItem failed for {}", entry.getNodeId(), e);
            entry.setStatus("Error: " + e.getMessage());
            if (onDataChange != null) onDataChange.accept(entry);
        }

    }

    private void subscribeEventNode(NodeId nodeId, OpcUaClient c) {

        try {
            if (eventSubscription == null) return;

            // Set a listener for data changes at the subscription level.
            eventSubscription.setSubscriptionListener(
                    new OpcUaSubscription.SubscriptionListener() {
                        @Override
                        public void onEventReceived(
                                OpcUaSubscription subscription,
                                List<OpcUaMonitoredItem> items,
                                List<Variant[]> fields) {

                            for (int i = 0; i < items.size(); i++) {
                                Variant[] variants = fields.get(i);

                                for (int j = 0; j < variants.length; j++) {
                                    LOG.info(
                                            "subscription onEventReceived: nodeId={}, field[{}]={}",
                                            items.get(i).getReadValueId().getNodeId(),
                                            j,
                                            variants[j].value());
                                }

                                EventEntry event = buildEvent(variants);
                                if (event != null && onEvent != null) onEvent.accept(event);
                            }

                        }
                    });

            EventFilter eventFilter = new EventFilter(
                    EVENT_FIELDS, new ContentFilter(null));

//            EventFilter eventFilter =
//                    new EventFilterBuilder()
//                            .select(NodeIds.BaseEventType, new QualifiedName(0, "EventId"))
//                            .select(NodeIds.BaseEventType, new QualifiedName(0, "EventType"))
//                            .select(NodeIds.BaseEventType, new QualifiedName(0, "Severity"))
//                            .select(NodeIds.BaseEventType, new QualifiedName(0, "Time"))
//                            .select(NodeIds.BaseEventType, new QualifiedName(0, "Message"))
//                            .build();

            var monitoredItem = OpcUaMonitoredItem.newEventItem(NodeIds.Server, eventFilter);

            eventSubscription.addMonitoredItem(monitoredItem);
            eventSubscription.synchronizeMonitoredItems();

            eventHandles.put(nodeId.toParseableString(), monitoredItem);

            LOG.info("Event node subscribed: {}", nodeId);
        } catch (Exception e) {
            LOG.error("subscribeEventNode failed for {}", nodeId, e);
            if (onError != null) onError.accept("eventNode", e);
        }
    }

    private EventEntry buildEvent(Variant[] variants) {
        if (variants == null || variants.length < EVENT_FIELD_NAMES.length)
            return null;
        Instant  time       = Instant.now();
        String   sourceNode = "";
        int      severity   = 0;
        String   message    = "";
        Map<String,String> fields = new LinkedHashMap<>();

        for (int i = 0; i < EVENT_FIELD_NAMES.length && i < variants.length; i++) {
            Object val = variants[i].getValue();
            String str = val == null ? "" : val.toString();
            fields.put(EVENT_FIELD_NAMES[i], str);
            switch (EVENT_FIELD_NAMES[i]) {
                case "Time"       -> { try { time = ((DateTime) val).getJavaInstant(); } catch (Exception ignored) {} }
                case "SourceNode" -> sourceNode = str;
                case "Severity"   -> { try { severity = ((Number) val).intValue(); } catch (Exception ignored) {} }
                case "Message"    -> { try { message = ((LocalizedText) val).getText(); } catch (Exception ignored) {} }
            }
        }
        return new EventEntry(time, sourceNode, severity, message, fields);
    }

    private void tearDown() {
        OpcUaClient c = client;
        if (c == null) return;
        executor(() -> {
            try {
                if (dataSubscription != null) {
                    dataSubscription.delete();
                    dataSubscription = null;
                }
            } catch (Exception e) { LOG.warn("teardown data subscription", e); }
            try {
                if (eventSubscription != null) {
                    eventSubscription.delete();
                    eventSubscription = null;
                }
            } catch (Exception e) { LOG.warn("teardown event subscription", e); }
            dataHandles.clear();
            eventHandles.clear();
        });
    }

    private static void executor(Runnable r) {
        Thread t = new Thread(r, "subscription-worker");
        t.setDaemon(true);
        t.start();
    }

    private static String valueStr(DataValue dv) {
        if (dv == null || dv.getValue() == null) return "";
        Object v = dv.getValue().getValue();
        return v == null ? "<null>" : v.toString();
    }

    private static String timeStr(DataValue dv) {
        if (dv == null || dv.getSourceTime() == null) return "";
        try { return FMT.format(dv.getSourceTime().getJavaInstant()); }
        catch (Exception e) { return ""; }
    }

    private static SimpleAttributeOperand eventField(String browseName) {
        return new SimpleAttributeOperand(
                NodeIds.BaseEventType,
                new QualifiedName[]{ new QualifiedName(0, browseName) },
                AttributeId.Value.uid(),
                null);
    }
}