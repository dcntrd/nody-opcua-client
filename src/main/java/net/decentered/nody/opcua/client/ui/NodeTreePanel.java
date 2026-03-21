package net.decentered.nody.opcua.client.ui;

import org.eclipse.milo.opcua.sdk.client.nodes.UaNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Left panel – lazily expanding JTree of the OPC UA address space.
 */
public class NodeTreePanel extends JPanel {

    // Tree node

    public static class OpcUaTreeNode extends DefaultMutableTreeNode {

        private final NodeId nodeId;
        private boolean childrenLoaded = false;

        /** Constructor for real UaNodes – adds a placeholder child so the expand arrow appears. */
        public OpcUaTreeNode(UaNode uaNode) {
            super(uaNode);
            this.nodeId = uaNode.getNodeId();
            add(new DefaultMutableTreeNode("Loading…"));
        }

        public OpcUaTreeNode(String label) {
            super(label);
            this.nodeId = null;
        }

        public NodeId  getNodeId()                       { return nodeId; }
        public boolean isChildrenLoaded()                { return childrenLoaded; }
        public void    setChildrenLoaded(boolean loaded) { this.childrenLoaded = loaded; }

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

    // Fields

    private final JTree             tree;
    private final DefaultTreeModel  treeModel;
    private final OpcUaTreeNode     root;

    private final Consumer<NodeId> onBrowseRequested;
    private final Consumer<NodeId> onNodeSelected;

    // Construction

    public NodeTreePanel(Consumer<NodeId> onBrowseRequested, Consumer<NodeId> onNodeSelected) {
        this.onBrowseRequested = onBrowseRequested;
        this.onNodeSelected    = onNodeSelected;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Address Space"));
        setPreferredSize(new Dimension(380, 0));

        root      = new OpcUaTreeNode("Root");
        treeModel = new DefaultTreeModel(root);
        tree      = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        // Lazy-load listener
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

        // Selection listener
        tree.addTreeSelectionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path == null) return;
            OpcUaTreeNode node = (OpcUaTreeNode) path.getLastPathComponent();
            if (node.getNodeId() != null) {
                onNodeSelected.accept(node.getNodeId());
            }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    // ── Public API (always called from EDT by MainFrame) ──────────────────────

    /**
     * Replaces the root's children with the browsed top-level nodes and
     * expands the root.  Called after the initial browse on connect.
     */
    public void setRootChildren(List<UaNode> children) {
        root.removeAllChildren();
        root.setChildrenLoaded(true);
        for (UaNode child : children) root.add(new OpcUaTreeNode(child));
        treeModel.nodeStructureChanged(root);
        tree.expandPath(new TreePath(root.getPath()));
    }

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

    public void clear() {
        root.removeAllChildren();
        root.setChildrenLoaded(false);
        treeModel.nodeStructureChanged(root);
    }

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