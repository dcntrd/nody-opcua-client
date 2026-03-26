package net.decentered.nody.opcua.client.model;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 * One row in the Data Subscription table.
 * All fields are mutable so the table model can update them in-place.
 */
public class MonitoredItemEntry {

    private final NodeId nodeId;
    private final String displayName;
    private double  samplingInterval;   // ms – editable per-item
    private String  value     = "";
    private String  timestamp = "";
    private String  status    = "Pending";

    public MonitoredItemEntry(NodeId nodeId, String displayName, double samplingInterval) {
        this.nodeId           = nodeId;
        this.displayName      = displayName;
        this.samplingInterval = samplingInterval;
    }

    public NodeId  getNodeId()           { return nodeId; }
    public String  getDisplayName()      { return displayName; }
    public double  getSamplingInterval() { return samplingInterval; }
    public String  getValue()            { return value; }
    public String  getTimestamp()        { return timestamp; }
    public String  getStatus()           { return status; }

    public void setSamplingInterval(double v) { this.samplingInterval = v; }
    public void setValue(String v)            { this.value     = v; }
    public void setTimestamp(String v)        { this.timestamp = v; }
    public void setStatus(String v)           { this.status    = v; }
}