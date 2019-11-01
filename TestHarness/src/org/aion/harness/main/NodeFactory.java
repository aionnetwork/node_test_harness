package org.aion.harness.main;

import java.util.NoSuchElementException;
import org.aion.harness.main.impl.ProxyJavaNode;
import org.aion.harness.main.impl.RustNode;
import org.aion.harness.main.impl.JavaNode;
import org.aion.harness.main.impl.GenericRemoteNode;
import org.aion.harness.main.impl.KubernetesNode;
import org.aion.harness.main.impl.KubernetesNodeType;

/**
 * A factory for producing Node implementations.
 *
 * A NodeFactory is not thread-safe.
 */
public final class NodeFactory {

    public enum NodeType { JAVA_NODE, RUST_NODE, PROXY_JAVA_NODE}

    public static LocalNode getNewLocalNodeInstance(NodeType node) {
        if (node == null) {
            throw new NullPointerException("Cannot get null node.");
        }

        switch (node) {
            case JAVA_NODE: return new JavaNode();
            case RUST_NODE: return new RustNode();
            case PROXY_JAVA_NODE: return new ProxyJavaNode();
            default: throw new NoSuchElementException("The provided node type is not yet supported: " + node);
        }
    }

    public static GenericRemoteNode getNewGenericRemoteNodeInstance(NodeType node) {
        // We don't do anything with the input node yet, but this will become a difference once we begin supporting
        // the rust kernel.

        if (node == null) {
            throw new NullPointerException("Cannot get null node.");
        }

        switch (node) {
            case JAVA_NODE: return new GenericRemoteNode();
            default: throw new NoSuchElementException("The provided node type is not yet supported: " + node);
        }
    }

    public static KubernetesNode GenerateKubernetesNodeInstance(NodeType node, KubernetesNodeType kubernetesNodeType) {
        if (kubernetesNodeType == null) {
            throw new NullPointerException("Cannot get null kubernetes node.");
        }

        switch (node) {
            case JAVA_NODE: return new KubernetesNode(kubernetesNodeType);
            default: throw new NoSuchElementException("The provided node type is not yet supported: " + node);
        }
    }
}
