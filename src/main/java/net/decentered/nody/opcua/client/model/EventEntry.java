package net.decentered.nody.opcua.client.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row in the Event view table.
 */
public class EventEntry {

    private final Instant             time;
    private final String              sourceNode;
    private final int                 severity;
    private final String              message;
    private final Map<String, String> fields;  // additional event fields

    public EventEntry(Instant time, String sourceNode, int severity,
                      String message, Map<String, String> fields) {
        this.time       = time;
        this.sourceNode = sourceNode;
        this.severity   = severity;
        this.message    = message;
        this.fields     = new LinkedHashMap<>(fields);
    }

    public Instant             getTime()       { return time; }
    public String              getSourceNode() { return sourceNode; }
    public int                 getSeverity()   { return severity; }
    public String              getMessage()    { return message; }
    public Map<String, String> getFields()     { return fields; }
}