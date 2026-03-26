package net.decentered.nody.opcua.client.ui;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

/**
 * Transferable that carries a {@link NodeId} during drag-and-drop from the
 * node tree to the subscription / event panels.
 */
public class NodeIdTransferable implements Transferable {

    /** DataFlavor used to transfer a {@link NodeId} within the same JVM. */
    public static final DataFlavor NODE_ID_FLAVOR;

    static {
        try {
            NODE_ID_FLAVOR = new DataFlavor(
                    DataFlavor.javaJVMLocalObjectMimeType +
                            ";class=org.eclipse.milo.opcua.stack.core.types.builtin.NodeId");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final NodeId   nodeId;
    private final String   displayName;

    public NodeIdTransferable(NodeId nodeId, String displayName) {
        this.nodeId      = nodeId;
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{ NODE_ID_FLAVOR };
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return NODE_ID_FLAVOR.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
        return nodeId;
    }
}