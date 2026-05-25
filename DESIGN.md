# javadb — Design Document

A small relational database written from scratch in Java 25 with no runtime
dependencies. It parses a subset of SQL, stores typed rows in page-based files
on disk through a buffer pool, keeps a write-ahead log for durability, builds
B+ tree indexes, and answers queries with filters, joins and aggregation. A
socket server accepts connections on virtual threads and a line client speaks
SQL to it.

## Goals

- A usable SQL subset: `CREATE TABLE`, `CREATE INDEX`, `INSERT`, `SELECT`,
  `UPDATE`, `DELETE`.
- Typed columns: `INT`, `LONG`, `DOUBLE`, `TEXT`, `BOOL`.
- Durable, page-based storage with a buffer pool and a write-ahead log.
- B+ tree indexes used for equality point lookups.
- A query executor with `WHERE` filters, `INNER JOIN`, `GROUP BY` aggregation
  and `ORDER BY`.
- Virtual-thread connection handling and a matching client.
- No third-party libraries in the main code. JUnit 5 only for tests.

## Non-goals

- Transactions across multiple statements (each statement auto-commits).
- A cost-based optimizer (index use is a single targeted rule).
- Concurrency beyond a single global write lock per database.
- The full SQL standard.

## Java 25 features used

- **Records** model immutable tuples, schema columns, tokens and AST nodes.
- **Sealed interfaces** describe the statement and expression trees, and the
  value domain, so the executor handles every case exhaustively.
- **Pattern matching for `switch`** drives statement dispatch and expression
  evaluation over those sealed types.
- **Virtual threads** (`Thread.ofVirtual`) handle one client connection each.
- **Foreign Function & Memory API** (`Arena`, `MemorySegment`) holds page
  buffers off the Java heap inside the buffer pool.

## Module map

```
com.javadb
  Main                       server bootstrap
  client.SqlClient           interactive SQL client over a socket
  server.DbServer, Session   accept loop on virtual threads, per-connection REPL
  sql.Lexer, Token, Parser   SQL text -> tokens -> AST
  sql.ast.*                  sealed Statement and Expr trees plus clause records
  types.DataType, Value      column types and the sealed value domain
  types.Values               comparison, coercion and arithmetic over values
  catalog.Column, TableSchema, Catalog   table and index definitions on disk
  storage.PageId, Page, PageOps          slotted page layout over a MemorySegment
  storage.DiskManager, BufferPool, Wal   page I/O, off-heap cache, redo log
  storage.RID, Tuples                    record ids and row (de)serialization
  engine.Row, Table, BPlusTree           row container, heap access, index
  engine.Database, Executor, QueryResult the engine facade and query runner
```

## Storage layer

### Pages and the slotted layout

The unit of storage is a 4096-byte page. Every table is a heap file of pages,
one file per table under the data directory. A page uses a slotted layout so
variable-length rows pack tightly and can be deleted in place:

```
+----------------------------------------------------------+
| slotCount (int) | freePointer (int) | slot[0] | slot[1] .. |   header grows down
| ...                                                        |
|                         free space                         |
| ..                                            row k | row 1 |   rows grow up
+----------------------------------------------------------+
```

- `slotCount` is the number of slots; `freePointer` is the offset where row
  data currently starts (rows are written from the end of the page toward the
  middle).
- Each slot is two ints: the row's offset and its length. A length of `-1`
  marks a deleted slot (a tombstone).
- A `RID` (record id) is `(pageNo, slotIndex)` and stays stable while the row
  lives, so indexes can point at rows.

### MemorySegment page buffers

`Page` wraps a `MemorySegment`. The buffer pool allocates these segments from a
shared `Arena`, keeping page memory off the Java heap. Bulk page I/O uses the
segment's `ByteBuffer` view against a `FileChannel`; field access uses unaligned
`ValueLayout` accessors.

### Buffer pool

`BufferPool` caches a bounded number of frames keyed by `PageId`. It tracks a
dirty flag per frame and evicts in least-recently-used order. Evicting a dirty
frame flushes it first. Freed off-heap segments are recycled through a free list
so eviction does not leak native memory.

### Write-ahead log and recovery

`Wal` is an append-only file of full page images: each record is
`(tableName, pageNo, pageImage)`. The rule is write-ahead — a page image is
logged and the log is forced before the page is written to its data file. A
checkpoint flushes all dirty pages and then truncates the log.

Recovery runs at startup: every record in the log is replayed by writing its
page image back to the data file, then the log is truncated. Because records are
full page images replayed in order, recovery is idempotent and the last image
for a page wins.

## Catalog

`Catalog` persists table schemas and index definitions as a small text file in
the data directory. Each line is either a table (`T:name:col,type;col,type;...`)
or an index (`I:name:table:column`). It is rewritten whenever a table or index
is created.

## Indexes

`BPlusTree` is an in-memory B+ tree of order 4. Keys are `Value`s ordered by a
type-aware comparator; each key maps to the list of `RID`s holding it, so
duplicate keys are supported. Insert splits full nodes and promotes a separator;
delete removes the rid and drops empty keys without merging (lookups stay
correct). Indexes are rebuilt by scanning the heap when the database opens and
maintained on every insert, update and delete.

## SQL front end

`Lexer` turns SQL text into words, numbers, strings and symbols. `Parser` is a
recursive-descent parser producing a sealed `Statement` tree. Expressions parse
with standard precedence: `OR`, `AND`, `NOT`, comparison, additive,
multiplicative, unary, primary. Primaries are literals, qualified column
references (`table.col`), parenthesised expressions, `*`, and function calls
(`COUNT`, `SUM`, `AVG`, `MIN`, `MAX`, including `COUNT(*)`).

## Execution

`Executor` switches over the sealed `Statement` type:

- **CREATE TABLE / CREATE INDEX** update the catalog and (for indexes) build the
  tree from a scan.
- **INSERT** coerces literal values to column types and writes rows through the
  heap, maintaining indexes.
- **SELECT** builds a row set from the base table and any `INNER JOIN`s with a
  nested-loop join evaluating each `ON` predicate, applies the `WHERE` filter,
  then either groups (when `GROUP BY` is present or any projection is an
  aggregate) or projects per row, and finally orders by a projected column. A
  single-table `WHERE col = value` on an indexed column uses the B+ tree instead
  of a full scan.
- **UPDATE / DELETE** scan (or index-probe) matching rows and rewrite or remove
  them, keeping indexes current.

Values are compared and combined through `Values`, which treats all numerics as
doubles for comparison, compares text lexicographically, and returns `false`
for any comparison involving `NULL`.

## Durability model

Each write statement auto-commits: after applying its changes the engine flushes
dirty pages (logging them write-ahead) and checkpoints the log. A crash between
the log force and the data write is repaired by replaying the log at the next
startup.

## Wire protocol

The client and server exchange null-terminated (`0x00`) UTF-8 messages over TCP.
The client sends one SQL message (which may contain several `;`-separated
statements); the server replies with the formatted result text. The server runs
the accept loop and spawns one virtual thread per connection; a single global
lock serializes statement execution against the shared engine state.

## Build and run

- `release.sh` runs `mvn clean package` and produces `target/javadb.jar`.
- `run.sh` packages if needed and starts the server (`java -jar`).
- `client.sh` starts the interactive SQL client against a running server.

## Testing

JUnit 5 tests cover the lexer, parser, value semantics, slotted page operations,
the B+ tree, write-ahead recovery, and end-to-end engine behavior including
inserts, filters, joins, aggregation, ordering, updates, deletes and indexed
lookups.
