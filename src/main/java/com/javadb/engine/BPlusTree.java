package com.javadb.engine;

import com.javadb.storage.RID;
import com.javadb.types.Value;
import com.javadb.types.Values;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BPlusTree {

    private static final int ORDER = 4;
    private static final int MAX_KEYS = ORDER - 1;

    private final Comparator<Value> cmp = Values::compare;
    private Node root = new Leaf();

    private abstract static class Node {
        final List<Value> keys = new ArrayList<>();
    }

    private static final class Leaf extends Node {
        final List<List<RID>> values = new ArrayList<>();
        Leaf next;
    }

    private static final class Internal extends Node {
        final List<Node> children = new ArrayList<>();
    }

    private record Split(Value key, Node right) {
    }

    public void put(Value key, RID rid) {
        Split split = insert(root, key, rid);
        if (split != null) {
            Internal newRoot = new Internal();
            newRoot.keys.add(split.key());
            newRoot.children.add(root);
            newRoot.children.add(split.right());
            root = newRoot;
        }
    }

    private Split insert(Node node, Value key, RID rid) {
        if (node instanceof Leaf leaf) {
            int found = find(leaf.keys, key);
            if (found >= 0) {
                leaf.values.get(found).add(rid);
                return null;
            }
            int pos = insertionPoint(leaf.keys, key);
            leaf.keys.add(pos, key);
            List<RID> list = new ArrayList<>();
            list.add(rid);
            leaf.values.add(pos, list);
            if (leaf.keys.size() > MAX_KEYS) {
                return splitLeaf(leaf);
            }
            return null;
        }
        Internal internal = (Internal) node;
        int child = childIndex(internal.keys, key);
        Split split = insert(internal.children.get(child), key, rid);
        if (split == null) {
            return null;
        }
        int pos = insertionPoint(internal.keys, split.key());
        internal.keys.add(pos, split.key());
        internal.children.add(pos + 1, split.right());
        if (internal.keys.size() > MAX_KEYS) {
            return splitInternal(internal);
        }
        return null;
    }

    private Split splitLeaf(Leaf leaf) {
        int mid = leaf.keys.size() / 2;
        Leaf right = new Leaf();
        right.keys.addAll(leaf.keys.subList(mid, leaf.keys.size()));
        right.values.addAll(leaf.values.subList(mid, leaf.values.size()));
        leaf.keys.subList(mid, leaf.keys.size()).clear();
        leaf.values.subList(mid, leaf.values.size()).clear();
        right.next = leaf.next;
        leaf.next = right;
        return new Split(right.keys.get(0), right);
    }

    private Split splitInternal(Internal node) {
        int mid = node.keys.size() / 2;
        Value up = node.keys.get(mid);
        Internal right = new Internal();
        right.keys.addAll(node.keys.subList(mid + 1, node.keys.size()));
        right.children.addAll(node.children.subList(mid + 1, node.children.size()));
        node.keys.subList(mid, node.keys.size()).clear();
        node.children.subList(mid + 1, node.children.size()).clear();
        return new Split(up, right);
    }

    public List<RID> get(Value key) {
        Node node = root;
        while (node instanceof Internal internal) {
            node = internal.children.get(childIndex(internal.keys, key));
        }
        Leaf leaf = (Leaf) node;
        int found = find(leaf.keys, key);
        return found >= 0 ? List.copyOf(leaf.values.get(found)) : List.of();
    }

    public void remove(Value key, RID rid) {
        Node node = root;
        while (node instanceof Internal internal) {
            node = internal.children.get(childIndex(internal.keys, key));
        }
        Leaf leaf = (Leaf) node;
        int found = find(leaf.keys, key);
        if (found < 0) {
            return;
        }
        leaf.values.get(found).remove(rid);
        if (leaf.values.get(found).isEmpty()) {
            leaf.keys.remove(found);
            leaf.values.remove(found);
        }
    }

    private int childIndex(List<Value> keys, Value key) {
        int i = 0;
        while (i < keys.size() && cmp.compare(key, keys.get(i)) >= 0) {
            i++;
        }
        return i;
    }

    private int insertionPoint(List<Value> keys, Value key) {
        int i = 0;
        while (i < keys.size() && cmp.compare(key, keys.get(i)) > 0) {
            i++;
        }
        return i;
    }

    private int find(List<Value> keys, Value key) {
        for (int i = 0; i < keys.size(); i++) {
            if (cmp.compare(key, keys.get(i)) == 0) {
                return i;
            }
        }
        return -1;
    }
}
