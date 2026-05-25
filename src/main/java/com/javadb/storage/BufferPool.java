package com.javadb.storage;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BufferPool implements AutoCloseable {

    private final int capacity;
    private final DiskManager disk;
    private final Wal wal;
    private final Arena arena = Arena.ofShared();
    private final Deque<MemorySegment> freeList = new ArrayDeque<>();
    private final LinkedHashMap<PageId, Frame> frames = new LinkedHashMap<>(16, 0.75f, true);

    public BufferPool(int capacity, DiskManager disk, Wal wal) {
        this.capacity = capacity;
        this.disk = disk;
        this.wal = wal;
    }

    private static final class Frame {
        final MemorySegment segment;
        boolean dirty;

        Frame(MemorySegment segment) {
            this.segment = segment;
        }
    }

    public Page fetch(PageId id) {
        Frame frame = frames.get(id);
        if (frame != null) {
            return new Page(frame.segment);
        }
        MemorySegment segment = takeSegment();
        Page page = new Page(segment);
        disk.readPage(id, page);
        evictIfNeeded();
        frames.put(id, new Frame(segment));
        return page;
    }

    public void markDirty(PageId id) {
        Frame frame = frames.get(id);
        if (frame != null) {
            frame.dirty = true;
        }
    }

    private void evictIfNeeded() {
        if (frames.size() < capacity) {
            return;
        }
        Map.Entry<PageId, Frame> eldest = frames.entrySet().iterator().next();
        PageId id = eldest.getKey();
        Frame frame = eldest.getValue();
        if (frame.dirty) {
            Page page = new Page(frame.segment);
            wal.append(id, page);
            wal.force();
            disk.writePage(id, page);
            disk.force();
        }
        frames.remove(id);
        freeList.push(frame.segment);
    }

    public void flushAll() {
        List<Map.Entry<PageId, Frame>> dirty = new ArrayList<>();
        for (Map.Entry<PageId, Frame> entry : frames.entrySet()) {
            if (entry.getValue().dirty) {
                dirty.add(entry);
            }
        }
        if (dirty.isEmpty()) {
            return;
        }
        for (Map.Entry<PageId, Frame> entry : dirty) {
            wal.append(entry.getKey(), new Page(entry.getValue().segment));
        }
        wal.force();
        for (Map.Entry<PageId, Frame> entry : dirty) {
            disk.writePage(entry.getKey(), new Page(entry.getValue().segment));
            entry.getValue().dirty = false;
        }
        disk.force();
        wal.truncate();
    }

    private MemorySegment takeSegment() {
        if (!freeList.isEmpty()) {
            return freeList.pop();
        }
        return arena.allocate(Page.PAGE_SIZE);
    }

    @Override
    public void close() {
        arena.close();
    }
}
