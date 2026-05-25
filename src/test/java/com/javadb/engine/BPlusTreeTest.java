package com.javadb.engine;

import com.javadb.storage.RID;
import com.javadb.types.Value;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BPlusTreeTest {

    @Test
    void findsKeysAfterManyInsertsForceSplits() {
        BPlusTree tree = new BPlusTree();
        for (int i = 0; i < 200; i++) {
            tree.put(new Value.IntVal(i), new RID(i, 0));
        }
        for (int i = 0; i < 200; i++) {
            List<RID> found = tree.get(new Value.IntVal(i));
            assertEquals(List.of(new RID(i, 0)), found);
        }
        assertTrue(tree.get(new Value.IntVal(999)).isEmpty());
    }

    @Test
    void keepsEveryRidForDuplicateKeys() {
        BPlusTree tree = new BPlusTree();
        tree.put(new Value.IntVal(7), new RID(1, 0));
        tree.put(new Value.IntVal(7), new RID(2, 0));
        assertEquals(2, tree.get(new Value.IntVal(7)).size());
    }

    @Test
    void removingLastRidDropsTheKey() {
        BPlusTree tree = new BPlusTree();
        tree.put(new Value.IntVal(7), new RID(1, 0));
        tree.put(new Value.IntVal(7), new RID(2, 0));
        tree.remove(new Value.IntVal(7), new RID(1, 0));
        assertEquals(List.of(new RID(2, 0)), tree.get(new Value.IntVal(7)));
        tree.remove(new Value.IntVal(7), new RID(2, 0));
        assertTrue(tree.get(new Value.IntVal(7)).isEmpty());
    }
}
