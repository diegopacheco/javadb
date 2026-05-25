package com.javadb.engine;

import com.javadb.catalog.Catalog;
import com.javadb.catalog.IndexDef;
import com.javadb.catalog.TableSchema;
import com.javadb.sql.Parser;
import com.javadb.sql.ast.Statement;
import com.javadb.storage.BufferPool;
import com.javadb.storage.DiskManager;
import com.javadb.storage.RID;
import com.javadb.storage.Wal;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Database implements AutoCloseable {

    private final DiskManager disk;
    private final Wal wal;
    private final BufferPool pool;
    private final Catalog catalog;
    private final Map<String, Table> tables = new HashMap<>();
    private final Map<String, List<IndexSlot>> indexes = new HashMap<>();
    private final Executor executor = new Executor(this);

    private record IndexSlot(String column, int columnIndex, BPlusTree tree) {
    }

    public Database(Path directory) {
        this.disk = new DiskManager(directory);
        this.wal = new Wal(directory);
        this.wal.recover(disk);
        this.pool = new BufferPool(256, disk, wal);
        this.catalog = new Catalog(directory);
        for (TableSchema schema : catalog.tables()) {
            tables.put(key(schema.name()), new Table(schema, pool, disk));
        }
        for (IndexDef def : catalog.indexes()) {
            buildIndex(def);
        }
    }

    public synchronized List<QueryResult> execute(String sql) {
        List<QueryResult> results = new ArrayList<>();
        for (Statement statement : Parser.parse(sql)) {
            results.add(executor.run(statement));
        }
        return results;
    }

    public TableSchema schema(String table) {
        return table(table).schema();
    }

    public boolean hasTable(String table) {
        return catalog.hasTable(table);
    }

    public List<Located> scan(String table) {
        return table(table).scan();
    }

    public Row fetch(String table, RID rid) {
        return table(table).get(rid);
    }

    public RID insert(String table, Row row) {
        RID rid = table(table).insert(row);
        for (IndexSlot slot : indexSlots(table)) {
            slot.tree().put(row.get(slot.columnIndex()), rid);
        }
        return rid;
    }

    public void delete(String table, RID rid, Row row) {
        table(table).delete(rid);
        for (IndexSlot slot : indexSlots(table)) {
            slot.tree().remove(row.get(slot.columnIndex()), rid);
        }
    }

    public RID update(String table, RID rid, Row oldRow, Row newRow) {
        RID newRid = table(table).update(rid, newRow);
        for (IndexSlot slot : indexSlots(table)) {
            slot.tree().remove(oldRow.get(slot.columnIndex()), rid);
            slot.tree().put(newRow.get(slot.columnIndex()), newRid);
        }
        return newRid;
    }

    public BPlusTree indexOn(String table, String column) {
        for (IndexSlot slot : indexSlots(table)) {
            if (slot.column().equalsIgnoreCase(column)) {
                return slot.tree();
            }
        }
        return null;
    }

    public void createTable(TableSchema schema) {
        catalog.addTable(schema);
        tables.put(key(schema.name()), new Table(schema, pool, disk));
    }

    public void createIndex(IndexDef def) {
        if (!catalog.hasTable(def.table())) {
            throw new IllegalArgumentException("unknown table: " + def.table());
        }
        catalog.addIndex(def);
        buildIndex(def);
    }

    public void commit() {
        pool.flushAll();
    }

    private void buildIndex(IndexDef def) {
        Table table = table(def.table());
        int columnIndex = table.schema().indexOf(def.column());
        if (columnIndex < 0) {
            throw new IllegalArgumentException("unknown column: " + def.column());
        }
        BPlusTree tree = new BPlusTree();
        for (Located located : table.scan()) {
            tree.put(located.row().get(columnIndex), located.rid());
        }
        indexes.computeIfAbsent(key(def.table()), unused -> new ArrayList<>())
                .add(new IndexSlot(def.column(), columnIndex, tree));
    }

    private List<IndexSlot> indexSlots(String table) {
        return indexes.getOrDefault(key(table), List.of());
    }

    private Table table(String name) {
        Table table = tables.get(key(name));
        if (table == null) {
            throw new IllegalArgumentException("unknown table: " + name);
        }
        return table;
    }

    private String key(String name) {
        return name.toLowerCase();
    }

    @Override
    public void close() {
        pool.flushAll();
        pool.close();
        wal.close();
        disk.close();
    }
}
