package net.decentered.nody.opcua.client.service;

import net.decentered.nody.opcua.client.model.NodeAttribute;
import org.eclipse.milo.opcua.sdk.client.nodes.UaNode;

import java.util.List;

/**
 * Listener interface that decouples the OPC UA client (business logic)
 * from the Swing UI. All callbacks are delivered on a background thread;
 * UI implementations must dispatch to the EDT themselves.
 */
public interface OpcUaClientListener {

    /** Called when the connection was established successfully. */
    void onConnected(String endpointUrl);

    /** Called when the connection was closed or lost. */
    void onDisconnected(String reason);

    /** Called when an error occurs during connect / browse / read. */
    void onError(String operation, Throwable cause);

    /**
     * Called when child nodes of a parent have been browsed.
     *
     * @param parentNodeId  the node-id string of the parent (null = root Objects folder)
     * @param children      the direct child nodes
     */
    void onNodesBrowsed(String parentNodeId, List<UaNode> children);

    /**
     * Called when a value has been successfully written to a node.
     *
     * @param nodeId the node-id string of the written node
     */
    void onWriteComplete(String nodeId);

    /**
     * Called when the attributes of a node have been read.
     *
     * @param nodeId     the node-id string of the queried node
     * @param attributes list of name/value attribute pairs
     */
    void onAttributesRead(String nodeId, List<NodeAttribute> attributes);
}