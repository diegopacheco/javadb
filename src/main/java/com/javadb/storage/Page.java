package com.javadb.storage;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class Page {

    public static final int PAGE_SIZE = 4096;
    private static final int HEADER = 8;
    private static final int SLOT_SIZE = 8;
    private static final int DELETED = -1;

    private final MemorySegment segment;

    public Page(MemorySegment segment) {
        this.segment = segment;
    }

    public MemorySegment segment() {
        return segment;
    }

    public void init() {
        setSlotCount(0);
        setFreePointer(PAGE_SIZE);
    }

    public int slotCount() {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 0);
    }

    private void setSlotCount(int count) {
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, 0, count);
    }

    private int freePointer() {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 4);
    }

    private void setFreePointer(int pointer) {
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, 4, pointer);
    }

    private int slotOffset(int slot) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, HEADER + slot * SLOT_SIZE);
    }

    private int slotLength(int slot) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, HEADER + slot * SLOT_SIZE + 4);
    }

    private void setSlot(int slot, int offset, int length) {
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER + slot * SLOT_SIZE, offset);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER + slot * SLOT_SIZE + 4, length);
    }

    public boolean hasSpace(int length) {
        int used = HEADER + slotCount() * SLOT_SIZE;
        int available = freePointer() - used;
        return available >= length + SLOT_SIZE;
    }

    public int insert(byte[] data) {
        if (!hasSpace(data.length)) {
            return -1;
        }
        int newPointer = freePointer() - data.length;
        MemorySegment.copy(data, 0, segment, ValueLayout.JAVA_BYTE, newPointer, data.length);
        int slot = slotCount();
        setSlot(slot, newPointer, data.length);
        setSlotCount(slot + 1);
        setFreePointer(newPointer);
        return slot;
    }

    public byte[] get(int slot) {
        if (slot < 0 || slot >= slotCount()) {
            return null;
        }
        int length = slotLength(slot);
        if (length == DELETED) {
            return null;
        }
        byte[] data = new byte[length];
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, slotOffset(slot), data, 0, length);
        return data;
    }

    public void delete(int slot) {
        if (slot >= 0 && slot < slotCount()) {
            setSlot(slot, slotOffset(slot), DELETED);
        }
    }
}
