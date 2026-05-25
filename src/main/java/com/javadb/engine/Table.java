package com.javadb.engine;

import com.javadb.catalog.TableSchema;
import com.javadb.storage.BufferPool;
import com.javadb.storage.DiskManager;
import com.javadb.storage.Page;
import com.javadb.storage.PageId;
import com.javadb.storage.RID;
import com.javadb.storage.Tuples;

import java.util.ArrayList;
import java.util.List;

public final class Table {

    private final TableSchema schema;
    private final BufferPool pool;
    private final DiskManager disk;

    public Table(TableSchema schema, BufferPool pool, DiskManager disk) {
        this.schema = schema;
        this.pool = pool;
        this.disk = disk;
    }

    public TableSchema schema() {
        return schema;
    }

    public RID insert(Row row) {
        byte[] data = Tuples.serialize(schema, row);
        int pages = disk.numPages(schema.name());
        for (int p = 0; p < pages; p++) {
            PageId id = new PageId(schema.name(), p);
            Page page = pool.fetch(id);
            int slot = page.insert(data);
            if (slot >= 0) {
                pool.markDirty(id);
                return new RID(p, slot);
            }
        }
        int p = disk.allocatePage(schema.name());
        PageId id = new PageId(schema.name(), p);
        Page page = pool.fetch(id);
        page.init();
        int slot = page.insert(data);
        if (slot < 0) {
            throw new IllegalStateException("row too large for a page");
        }
        pool.markDirty(id);
        return new RID(p, slot);
    }

    public List<Located> scan() {
        List<Located> rows = new ArrayList<>();
        int pages = disk.numPages(schema.name());
        for (int p = 0; p < pages; p++) {
            Page page = pool.fetch(new PageId(schema.name(), p));
            int count = page.slotCount();
            for (int s = 0; s < count; s++) {
                byte[] data = page.get(s);
                if (data == null) {
                    continue;
                }
                rows.add(new Located(new RID(p, s), Tuples.deserialize(schema, data)));
            }
        }
        return rows;
    }

    public Row get(RID rid) {
        Page page = pool.fetch(new PageId(schema.name(), rid.pageNo()));
        byte[] data = page.get(rid.slot());
        return data == null ? null : Tuples.deserialize(schema, data);
    }

    public void delete(RID rid) {
        PageId id = new PageId(schema.name(), rid.pageNo());
        Page page = pool.fetch(id);
        page.delete(rid.slot());
        pool.markDirty(id);
    }

    public RID update(RID rid, Row row) {
        delete(rid);
        return insert(row);
    }
}
