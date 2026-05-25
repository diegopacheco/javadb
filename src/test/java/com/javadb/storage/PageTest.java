package com.javadb.storage;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageTest {

    private Page newPage(Arena arena) {
        MemorySegment segment = arena.allocate(Page.PAGE_SIZE);
        Page page = new Page(segment);
        page.init();
        return page;
    }

    @Test
    void insertsAndReadsRowsBack() {
        try (Arena arena = Arena.ofConfined()) {
            Page page = newPage(arena);
            int first = page.insert("alpha".getBytes(StandardCharsets.UTF_8));
            int second = page.insert("beta".getBytes(StandardCharsets.UTF_8));
            assertEquals(0, first);
            assertEquals(1, second);
            assertArrayEquals("alpha".getBytes(StandardCharsets.UTF_8), page.get(first));
            assertArrayEquals("beta".getBytes(StandardCharsets.UTF_8), page.get(second));
            assertEquals(2, page.slotCount());
        }
    }

    @Test
    void deletedSlotReadsAsNullButKeepsSlotIndexes() {
        try (Arena arena = Arena.ofConfined()) {
            Page page = newPage(arena);
            int a = page.insert("a".getBytes(StandardCharsets.UTF_8));
            int b = page.insert("b".getBytes(StandardCharsets.UTF_8));
            page.delete(a);
            assertNull(page.get(a));
            assertArrayEquals("b".getBytes(StandardCharsets.UTF_8), page.get(b));
        }
    }

    @Test
    void reportsWhenSpaceIsExhausted() {
        try (Arena arena = Arena.ofConfined()) {
            Page page = newPage(arena);
            byte[] big = new byte[Page.PAGE_SIZE];
            assertTrue(page.insert(big) < 0);
        }
    }
}
