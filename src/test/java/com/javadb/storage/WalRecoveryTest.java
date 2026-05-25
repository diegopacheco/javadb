package com.javadb.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WalRecoveryTest {

    @TempDir
    Path dir;

    @Test
    void replaysLoggedPageImageThatNeverReachedTheDataFile() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(Page.PAGE_SIZE);
            Page page = new Page(segment);
            page.init();
            page.insert("durable".getBytes(StandardCharsets.UTF_8));

            Wal wal = new Wal(dir);
            wal.append(new PageId("t", 0), page);
            wal.force();
            wal.close();

            DiskManager disk = new DiskManager(dir);
            Wal recovery = new Wal(dir);
            recovery.recover(disk);

            MemorySegment restoredSegment = arena.allocate(Page.PAGE_SIZE);
            Page restored = new Page(restoredSegment);
            disk.readPage(new PageId("t", 0), restored);

            assertEquals(1, restored.slotCount());
            assertArrayEquals("durable".getBytes(StandardCharsets.UTF_8), restored.get(0));

            recovery.close();
            disk.close();
        }
    }

    @Test
    void recoveryEmptiesTheLogSoItIsNotReappliedTwice() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(Page.PAGE_SIZE);
            Page page = new Page(segment);
            page.init();
            page.insert("once".getBytes(StandardCharsets.UTF_8));

            Wal wal = new Wal(dir);
            wal.append(new PageId("t", 0), page);
            wal.force();
            wal.close();

            DiskManager disk = new DiskManager(dir);
            Wal recovery = new Wal(dir);
            recovery.recover(disk);
            recovery.close();

            DiskManager disk2 = new DiskManager(dir);
            Wal recovery2 = new Wal(dir);
            recovery2.recover(disk2);

            MemorySegment restoredSegment = arena.allocate(Page.PAGE_SIZE);
            Page restored = new Page(restoredSegment);
            disk2.readPage(new PageId("t", 0), restored);
            assertEquals(1, restored.slotCount());

            recovery2.close();
            disk.close();
            disk2.close();
        }
    }
}
