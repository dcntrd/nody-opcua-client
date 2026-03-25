package net.decentered.nody.opcua.client.ui;

import org.eclipse.milo.opcua.sdk.client.nodes.UaNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;

/**
 * Left panel – lazily expanding JTree of the OPC UA address space.
 *
 * <h3>Lazy-load sequence (fix for double-click issue)</h3>
 * The original code called {@code treeModel.nodeStructureChanged()} inside
 * {@code treeWillExpand()}, which caused Swing to cancel the in-progress
 * expansion.  The result was that children arrived in the model but the node
 * stayed visually collapsed, requiring a second click on the arrow.
 *
 * <p>The corrected approach:</p>
 * <ol>
 *   <li>{@code treeWillExpand} – mark the node as loading and fire the browse
 *       request, but do <em>not</em> touch the model at all.  The dummy
 *       "Loading…" child stays in place so the expand arrow is visible and
 *       Swing can complete the expansion uninterrupted.</li>
 *   <li>{@code appendChildren} (called from EDT when browse results arrive) –
 *       replace the dummy child with real nodes, then call
 *       {@code tree.expandPath()} so the node opens automatically without
 *       requiring a second user gesture.</li>
 * </ol>
 */
public class NodeTreePanel extends JPanel {

    // ── Tree node ─────────────────────────────────────────────────────────────

    public static class OpcUaTreeNode extends DefaultMutableTreeNode {

        private final NodeId    nodeId;
        private final NodeClass nodeClass;
        private boolean childrenLoaded = false;

        /** Constructor for real UaNodes – adds a placeholder child so the expand arrow appears. */
        public OpcUaTreeNode(UaNode uaNode) {
            super(uaNode);
            this.nodeId    = uaNode.getNodeId();
            this.nodeClass = uaNode.getNodeClass();
            add(new DefaultMutableTreeNode("Loading…"));
        }

        /** Constructor for the root node – no placeholder child needed. */
        public OpcUaTreeNode(String label) {
            super(label);
            this.nodeId    = null;
            this.nodeClass = null;
        }

        public NodeId    getNodeId()                       { return nodeId; }
        public NodeClass getNodeClass()                    { return nodeClass; }
        public boolean   isVariable()                      { return nodeClass == NodeClass.Variable; }
        public boolean   isChildrenLoaded()                { return childrenLoaded; }
        public void      setChildrenLoaded(boolean loaded) { this.childrenLoaded = loaded; }

        @Override
        public String toString() {
            Object uo = getUserObject();
            if (uo instanceof UaNode n) {
                return n.getDisplayName().getText()
                        + "  [" + n.getNodeId().toParseableString() + "]";
            }
            return String.valueOf(uo);
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final JTree             tree;
    private final DefaultTreeModel  treeModel;
    private final OpcUaTreeNode     root;

    private final Consumer<NodeId>  onBrowseRequested;
    private final Consumer<NodeId>  onNodeSelected;
    /** Resolves a namespace index to its URI; called on the EDT (non-blocking). */
    private final IntFunction<String> namespaceUriResolver;
    /** Called when the user chooses Write Value from the context menu. */
    private final BiConsumer<NodeId, Variant> onWriteValue;

    // ── Construction ──────────────────────────────────────────────────────────

    public NodeTreePanel(Consumer<NodeId> onBrowseRequested,
                         Consumer<NodeId> onNodeSelected,
                         IntFunction<String> namespaceUriResolver,
                         BiConsumer<NodeId, Variant> onWriteValue) {
        this.onBrowseRequested    = onBrowseRequested;
        this.onNodeSelected       = onNodeSelected;
        this.namespaceUriResolver = namespaceUriResolver;
        this.onWriteValue         = onWriteValue;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Address Space"));
        setPreferredSize(new Dimension(380, 0));

        root      = new OpcUaTreeNode("Root");
        treeModel = new DefaultTreeModel(root);
        tree      = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        // ── Lazy-load listener ────────────────────────────────────────────────
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                TreePath path = event.getPath();
                OpcUaTreeNode node = (OpcUaTreeNode) path.getLastPathComponent();
                if (!node.isChildrenLoaded()) {
                    // Mark immediately so a second expand event cannot queue a
                    // duplicate browse request while the first is in flight.
                    node.setChildrenLoaded(true);

                    // DO NOT touch the model here.  Calling nodeStructureChanged()
                    // inside treeWillExpand cancels the current expansion and forces
                    // the user to click the arrow a second time.
                    // The "Loading…" placeholder stays visible until appendChildren()
                    // replaces it and calls expandPath() to finish the job.
                    onBrowseRequested.accept(node.getNodeId());
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) { }
        });

        // ── Selection listener ────────────────────────────────────────────────
        tree.addTreeSelectionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path == null) return;
            OpcUaTreeNode node = (OpcUaTreeNode) path.getLastPathComponent();
            if (node.getNodeId() != null) {
                onNodeSelected.accept(node.getNodeId());
            }
        });

        // ── Context menu ──────────────────────────────────────────────────
        JPopupMenu contextMenu = buildContextMenu();
        tree.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { maybeShowPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }

            private void maybeShowPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                // Select the node under the pointer before showing the menu
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path != null) tree.setSelectionPath(path);
                contextMenu.show(tree, e.getX(), e.getY());
            }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    // ── Context menu ──────────────────────────────────────────────────────

    private JPopupMenu buildContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem copyNodeId = new JMenuItem("Copy Node Identifier");
        copyNodeId.setToolTipText(
                "Copy the full NodeId string to the clipboard  –  e.g. ns=2;s=MyNode");
        copyNodeId.addActionListener(e -> {
            NodeId nodeId = selectedNodeId();
            if (nodeId != null) copyToClipboard(nodeId.toParseableString());
        });

        JMenuItem copyNamespace = new JMenuItem("Copy Namespace URI");
        copyNamespace.setToolTipText(
                "Copy the namespace URI for the selected node");
        copyNamespace.addActionListener(e -> {
            NodeId nodeId = selectedNodeId();
            if (nodeId != null) {
                String uri = namespaceUriResolver.apply(nodeId.getNamespaceIndex().intValue());
                copyToClipboard(uri);
            }
        });

        JMenuItem writeValue = new JMenuItem("Write Value…");
        writeValue.setToolTipText(
                "Write a new value to this Variable node");
        writeValue.addActionListener(e -> handleWriteValue());

        menu.add(copyNodeId);
        menu.addSeparator();
        menu.add(copyNamespace);
        menu.addSeparator();
        menu.add(writeValue);

        // Enable Write Value only for Variable nodes
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(
                    javax.swing.event.PopupMenuEvent e) {
                OpcUaTreeNode sel = selectedTreeNode();
                writeValue.setEnabled(sel != null && sel.isVariable());
            }
            @Override public void popupMenuWillBecomeInvisible(
                    javax.swing.event.PopupMenuEvent e) { }
            @Override public void popupMenuCanceled(
                    javax.swing.event.PopupMenuEvent e) { }
        });

        return menu;
    }

    private void handleWriteValue() {
        OpcUaTreeNode treeNode = selectedTreeNode();
        if (treeNode == null || !treeNode.isVariable()) return;

        NodeId nodeId = treeNode.getNodeId();
        // Get current value and dataType from the UaNode user object
        String currentValue = "";
        String dataType     = "";
        if (treeNode.getUserObject() instanceof UaNode uaNode) {
            try { currentValue = String.valueOf(
                    uaNode.readAttribute(
                                    org.eclipse.milo.opcua.stack.core.AttributeId.Value)
                            .getValue().getValue()); }
            catch (Exception ignored) { }
            try { dataType = String.valueOf(
                    uaNode.readAttribute(
                                    org.eclipse.milo.opcua.stack.core.AttributeId.DataType)
                            .getValue().getValue()); }
            catch (Exception ignored) { }
        }

        Frame frame = (Frame) SwingUtilities.getWindowAncestor(this);
        new WriteValueDialog(frame, nodeId, currentValue, dataType,
                (nid, variant) -> onWriteValue.accept(nid, variant))
                .setVisible(true);
    }

    /** Returns the selected {@link OpcUaTreeNode}, or {@code null}. */
    private OpcUaTreeNode selectedTreeNode() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;
        Object last = path.getLastPathComponent();
        return (last instanceof OpcUaTreeNode n) ? n : null;
    }

    /** Returns the {@link NodeId} of the currently selected tree node, or {@code null}. */
    private NodeId selectedNodeId() {
        OpcUaTreeNode n = selectedTreeNode();
        return n != null ? n.getNodeId() : null;
    }

    private static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(text), null);
    }

    // ── Public API (always called from EDT by MainFrame) ──────────────────────

    /**
     * Replaces the root's children with the browsed top-level nodes and
     * expands the root.  Called after the initial browse on connect.
     */
    public void setRootChildren(List<UaNode> children) {
        root.removeAllChildren();
        root.setChildrenLoaded(true);
        for (UaNode child : deduplicate(children)) root.add(new OpcUaTreeNode(child));
        treeModel.nodeStructureChanged(root);
        tree.expandPath(new TreePath(root.getPath()));
    }

    /**
     * Replaces the "Loading…" placeholder with real children for every tree
     * node instance whose NodeId matches {@code parentNodeIdStr}, then expands
     * each one.
     *
     * <h3>Why "all matching nodes"?</h3>
     * OPC UA models a graph, not a tree. The same node can be reachable via
     * multiple hierarchical reference paths (e.g. {@code Objects/MyDevice} and
     * {@code Objects/DeviceSet/MyDevice} both point to the same NodeId).  Each
     * path produces a separate {@link OpcUaTreeNode} instance in the Swing tree,
     * so updating only the first match (as the old {@code findNode} did) left
     * every other instance stuck on "Loading…".
     */
    public void appendChildren(String parentNodeIdStr, List<UaNode> children) {
        List<OpcUaTreeNode> matches = findAllNodes(root, parentNodeIdStr);
        if (matches.isEmpty()) return;

        List<UaNode> unique = deduplicate(children);
        for (OpcUaTreeNode parentNode : matches) {
            // Replace placeholder with real children
            parentNode.removeAllChildren();
            for (UaNode child : unique) parentNode.add(new OpcUaTreeNode(child));
            treeModel.nodeStructureChanged(parentNode);

            // Expand so the user sees the results without clicking again
            tree.expandPath(new TreePath(parentNode.getPath()));
        }
    }

    /** Resets the tree to a truly empty state (no placeholder). */
    public void clear() {
        root.removeAllChildren();
        root.setChildrenLoaded(false);
        treeModel.nodeStructureChanged(root);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns every {@link OpcUaTreeNode} in the subtree rooted at
     * {@code current} whose NodeId string equals {@code nodeIdStr}.
     * Multiple matches occur when the same OPC UA node is reachable via
     * more than one hierarchical reference path.
     */
    private List<OpcUaTreeNode> findAllNodes(OpcUaTreeNode current, String nodeIdStr) {
        List<OpcUaTreeNode> result = new ArrayList<>();
        collectNodes(current, nodeIdStr, result);
        return result;
    }

    private void collectNodes(OpcUaTreeNode current, String nodeIdStr,
                              List<OpcUaTreeNode> result) {
        if (current.getNodeId() != null &&
                current.getNodeId().toParseableString().equals(nodeIdStr)) {
            result.add(current);
        }
        for (int i = 0; i < current.getChildCount(); i++) {
            if (current.getChildAt(i) instanceof OpcUaTreeNode tn) {
                collectNodes(tn, nodeIdStr, result);
            }
        }
    }

    /**
     * Deduplicates a browse result by NodeId, preserving encounter order.
     * Servers may return the same node via multiple reference types
     * (e.g. both Organizes and HasComponent pointing to the same NodeId),
     * which would otherwise create two identical-looking entries in the tree.
     */
    private static List<UaNode> deduplicate(List<UaNode> nodes) {
        Map<String, UaNode> seen = new LinkedHashMap<>();
        for (UaNode n : nodes) {
            seen.putIfAbsent(n.getNodeId().toParseableString(), n);
        }
        return new ArrayList<>(seen.values());
    }
}