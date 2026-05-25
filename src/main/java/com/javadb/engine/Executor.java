package com.javadb.engine;

import com.javadb.catalog.IndexDef;
import com.javadb.catalog.TableSchema;
import com.javadb.sql.ast.Expr;
import com.javadb.sql.ast.Statement;
import com.javadb.storage.RID;
import com.javadb.types.Value;
import com.javadb.types.Values;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Executor {

    private final Database db;

    public Executor(Database db) {
        this.db = db;
    }

    private record Source(String alias, TableSchema schema, Row row) {
    }

    public QueryResult run(Statement statement) {
        return switch (statement) {
            case Statement.CreateTable create -> runCreateTable(create);
            case Statement.CreateIndex create -> runCreateIndex(create);
            case Statement.Insert insert -> runInsert(insert);
            case Statement.Select select -> runSelect(select);
            case Statement.Update update -> runUpdate(update);
            case Statement.Delete delete -> runDelete(delete);
        };
    }

    private QueryResult runCreateTable(Statement.CreateTable create) {
        db.createTable(new TableSchema(create.table(), create.columns()));
        db.commit();
        return QueryResult.message("CREATE TABLE " + create.table());
    }

    private QueryResult runCreateIndex(Statement.CreateIndex create) {
        db.createIndex(new IndexDef(create.name(), create.table(), create.column()));
        db.commit();
        return QueryResult.message("CREATE INDEX " + create.name());
    }

    private QueryResult runInsert(Statement.Insert insert) {
        TableSchema schema = db.schema(insert.table());
        int count = 0;
        for (List<Expr> rowExprs : insert.rows()) {
            Row row = buildInsertRow(schema, insert.columns(), rowExprs);
            db.insert(insert.table(), row);
            count++;
        }
        db.commit();
        return QueryResult.message("INSERT " + count);
    }

    private Row buildInsertRow(TableSchema schema, List<String> columns, List<Expr> exprs) {
        Value[] values = new Value[schema.columns().size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = Value.NULL;
        }
        if (columns == null) {
            if (exprs.size() != values.length) {
                throw new IllegalArgumentException("expected " + values.length + " values but found " + exprs.size());
            }
            for (int i = 0; i < exprs.size(); i++) {
                values[i] = Values.coerce(eval(exprs.get(i), List.of()), schema.columns().get(i).type());
            }
        } else {
            if (columns.size() != exprs.size()) {
                throw new IllegalArgumentException("column count does not match value count");
            }
            for (int i = 0; i < columns.size(); i++) {
                int index = schema.indexOf(columns.get(i));
                if (index < 0) {
                    throw new IllegalArgumentException("unknown column: " + columns.get(i));
                }
                values[index] = Values.coerce(eval(exprs.get(i), List.of()), schema.columns().get(index).type());
            }
        }
        return new Row(List.of(values));
    }

    private QueryResult runUpdate(Statement.Update update) {
        TableSchema schema = db.schema(update.table());
        int count = 0;
        for (Located located : candidates(update.table(), update.where())) {
            List<Source> sources = List.of(new Source(update.table(), schema, located.row()));
            if (update.where() != null && !Values.truthy(eval(update.where(), sources))) {
                continue;
            }
            List<Value> values = new ArrayList<>(located.row().values());
            for (Statement.Assignment assignment : update.assignments()) {
                int index = schema.indexOf(assignment.column());
                if (index < 0) {
                    throw new IllegalArgumentException("unknown column: " + assignment.column());
                }
                Value value = Values.coerce(eval(assignment.value(), sources), schema.columns().get(index).type());
                values.set(index, value);
            }
            db.update(update.table(), located.rid(), located.row(), new Row(values));
            count++;
        }
        db.commit();
        return QueryResult.message("UPDATE " + count);
    }

    private QueryResult runDelete(Statement.Delete delete) {
        TableSchema schema = db.schema(delete.table());
        int count = 0;
        for (Located located : candidates(delete.table(), delete.where())) {
            List<Source> sources = List.of(new Source(delete.table(), schema, located.row()));
            if (delete.where() != null && !Values.truthy(eval(delete.where(), sources))) {
                continue;
            }
            db.delete(delete.table(), located.rid(), located.row());
            count++;
        }
        db.commit();
        return QueryResult.message("DELETE " + count);
    }

    private QueryResult runSelect(Statement.Select select) {
        List<Source> layout = layout(select);
        List<List<Source>> combos = buildRows(select);
        if (select.where() != null) {
            List<List<Source>> filtered = new ArrayList<>();
            for (List<Source> combo : combos) {
                if (Values.truthy(eval(select.where(), combo))) {
                    filtered.add(combo);
                }
            }
            combos = filtered;
        }
        boolean aggregated = isAggregated(select);
        QueryResult result = aggregated ? projectAggregated(select, combos, layout)
                : projectRows(select, combos, layout);
        return orderBy(select, result);
    }

    private List<Source> layout(Statement.Select select) {
        List<Source> layout = new ArrayList<>();
        layout.add(new Source(aliasOf(select.alias(), select.table()), db.schema(select.table()), null));
        for (Statement.JoinClause join : select.joins()) {
            layout.add(new Source(aliasOf(join.alias(), join.table()), db.schema(join.table()), null));
        }
        return layout;
    }

    private List<List<Source>> buildRows(Statement.Select select) {
        List<List<Source>> combos = new ArrayList<>();
        String baseAlias = aliasOf(select.alias(), select.table());
        TableSchema baseSchema = db.schema(select.table());
        List<Located> base = select.joins().isEmpty()
                ? candidates(select.table(), select.where())
                : db.scan(select.table());
        for (Located located : base) {
            List<Source> combo = new ArrayList<>();
            combo.add(new Source(baseAlias, baseSchema, located.row()));
            combos.add(combo);
        }
        for (Statement.JoinClause join : select.joins()) {
            String alias = aliasOf(join.alias(), join.table());
            TableSchema schema = db.schema(join.table());
            List<Located> right = db.scan(join.table());
            List<List<Source>> next = new ArrayList<>();
            for (List<Source> combo : combos) {
                for (Located located : right) {
                    List<Source> candidate = new ArrayList<>(combo);
                    candidate.add(new Source(alias, schema, located.row()));
                    if (Values.truthy(eval(join.on(), candidate))) {
                        next.add(candidate);
                    }
                }
            }
            combos = next;
        }
        return combos;
    }

    private QueryResult projectRows(Statement.Select select, List<List<Source>> combos, List<Source> layout) {
        List<String> columns = new ArrayList<>();
        for (Statement.SelectItem item : select.items()) {
            if (item.expr() instanceof Expr.Star) {
                columns.addAll(starColumns(layout));
            } else {
                columns.add(columnName(item));
            }
        }
        List<List<Value>> rows = new ArrayList<>();
        for (List<Source> combo : combos) {
            List<Value> row = new ArrayList<>();
            for (Statement.SelectItem item : select.items()) {
                if (item.expr() instanceof Expr.Star) {
                    for (Source source : combo) {
                        row.addAll(source.row().values());
                    }
                } else {
                    row.add(eval(item.expr(), combo));
                }
            }
            rows.add(row);
        }
        return QueryResult.of(columns, rows);
    }

    private QueryResult projectAggregated(Statement.Select select, List<List<Source>> combos, List<Source> layout) {
        for (Statement.SelectItem item : select.items()) {
            if (item.expr() instanceof Expr.Star) {
                throw new IllegalArgumentException("* cannot be combined with aggregation");
            }
        }
        List<Expr> groupExprs = new ArrayList<>();
        for (String column : select.groupBy()) {
            groupExprs.add(columnRef(column));
        }
        Map<List<Value>, List<List<Source>>> groups = new LinkedHashMap<>();
        for (List<Source> combo : combos) {
            List<Value> keyValues = new ArrayList<>();
            for (Expr groupExpr : groupExprs) {
                keyValues.add(eval(groupExpr, combo));
            }
            groups.computeIfAbsent(keyValues, unused -> new ArrayList<>()).add(combo);
        }
        if (groups.isEmpty() && groupExprs.isEmpty()) {
            groups.put(List.of(), new ArrayList<>());
        }
        List<String> columns = new ArrayList<>();
        for (Statement.SelectItem item : select.items()) {
            columns.add(columnName(item));
        }
        List<List<Value>> rows = new ArrayList<>();
        for (List<List<Source>> group : groups.values()) {
            List<Value> row = new ArrayList<>();
            for (Statement.SelectItem item : select.items()) {
                row.add(evalAggregated(item.expr(), group));
            }
            rows.add(row);
        }
        return QueryResult.of(columns, rows);
    }

    private QueryResult orderBy(Statement.Select select, QueryResult result) {
        if (select.orderBy().isEmpty() || !result.isQuery()) {
            return result;
        }
        List<List<Value>> rows = new ArrayList<>(result.rows());
        List<Statement.OrderKey> keys = select.orderBy();
        rows.sort((a, b) -> {
            for (Statement.OrderKey key : keys) {
                int index = indexOfColumn(result.columns(), key.column());
                if (index < 0) {
                    throw new IllegalArgumentException("unknown order by column: " + key.column());
                }
                int cmp = Values.compare(a.get(index), b.get(index));
                if (cmp != 0) {
                    return key.ascending() ? cmp : -cmp;
                }
            }
            return 0;
        });
        return QueryResult.of(result.columns(), rows);
    }

    private List<Located> candidates(String table, Expr where) {
        if (where instanceof Expr.Compare compare && compare.op().equals("=")) {
            Expr.ColumnRef columnRef = null;
            Expr literal = null;
            if (compare.left() instanceof Expr.ColumnRef ref && !(compare.right() instanceof Expr.ColumnRef)) {
                columnRef = ref;
                literal = compare.right();
            } else if (compare.right() instanceof Expr.ColumnRef ref && !(compare.left() instanceof Expr.ColumnRef)) {
                columnRef = ref;
                literal = compare.left();
            }
            if (columnRef != null && belongsTo(columnRef, table)) {
                BPlusTree index = db.indexOn(table, columnRef.name());
                if (index != null) {
                    TableSchema schema = db.schema(table);
                    int columnIndex = schema.indexOf(columnRef.name());
                    Value key = Values.coerce(eval(literal, List.of()), schema.columns().get(columnIndex).type());
                    List<Located> located = new ArrayList<>();
                    for (RID rid : index.get(key)) {
                        Row row = db.fetch(table, rid);
                        if (row != null) {
                            located.add(new Located(rid, row));
                        }
                    }
                    return located;
                }
            }
        }
        return db.scan(table);
    }

    private boolean belongsTo(Expr.ColumnRef ref, String table) {
        return ref.table() == null || ref.table().equalsIgnoreCase(table);
    }

    private boolean isAggregated(Statement.Select select) {
        if (!select.groupBy().isEmpty()) {
            return true;
        }
        for (Statement.SelectItem item : select.items()) {
            if (containsAggregate(item.expr())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAggregate(Expr expr) {
        return switch (expr) {
            case Expr.Func func -> isAggregate(func.name()) || func.args().stream().anyMatch(this::containsAggregate);
            case Expr.Arith arith -> containsAggregate(arith.left()) || containsAggregate(arith.right());
            case Expr.Compare compare -> containsAggregate(compare.left()) || containsAggregate(compare.right());
            case Expr.Logical logical -> containsAggregate(logical.left()) || containsAggregate(logical.right());
            case Expr.Not not -> containsAggregate(not.inner());
            case Expr.ColumnRef ignored -> false;
            case Expr.Lit ignored -> false;
            case Expr.Star ignored -> false;
        };
    }

    private boolean isAggregate(String name) {
        return switch (name) {
            case "COUNT", "SUM", "AVG", "MIN", "MAX" -> true;
            default -> false;
        };
    }

    private Value eval(Expr expr, List<Source> sources) {
        return switch (expr) {
            case Expr.Lit lit -> lit.value();
            case Expr.ColumnRef ref -> lookup(ref, sources);
            case Expr.Arith arith -> Values.arithmetic(arith.op(), eval(arith.left(), sources), eval(arith.right(), sources));
            case Expr.Compare compare -> compareValues(compare.op(), eval(compare.left(), sources), eval(compare.right(), sources));
            case Expr.Logical logical -> evalLogical(logical, sources);
            case Expr.Not not -> new Value.BoolVal(!Values.truthy(eval(not.inner(), sources)));
            case Expr.Func func -> throw new IllegalArgumentException("aggregate " + func.name() + " is not allowed here");
            case Expr.Star ignored -> throw new IllegalArgumentException("* is not allowed in this position");
        };
    }

    private Value evalAggregated(Expr expr, List<List<Source>> group) {
        return switch (expr) {
            case Expr.Func func -> aggregate(func, group);
            case Expr.Lit lit -> lit.value();
            case Expr.Arith arith -> Values.arithmetic(arith.op(), evalAggregated(arith.left(), group), evalAggregated(arith.right(), group));
            case Expr.Compare compare -> compareValues(compare.op(), evalAggregated(compare.left(), group), evalAggregated(compare.right(), group));
            case Expr.Logical logical -> {
                Value left = evalAggregated(logical.left(), group);
                Value right = evalAggregated(logical.right(), group);
                boolean result = logical.op().equals("AND")
                        ? Values.truthy(left) && Values.truthy(right)
                        : Values.truthy(left) || Values.truthy(right);
                yield new Value.BoolVal(result);
            }
            case Expr.Not not -> new Value.BoolVal(!Values.truthy(evalAggregated(not.inner(), group)));
            case Expr.ColumnRef ref -> group.isEmpty() ? Value.NULL : lookup(ref, group.getFirst());
            case Expr.Star ignored -> throw new IllegalArgumentException("* is not allowed in this position");
        };
    }

    private Value aggregate(Expr.Func func, List<List<Source>> group) {
        if (func.name().equals("COUNT") && func.star()) {
            return new Value.LongVal(group.size());
        }
        Expr arg = func.args().getFirst();
        List<Value> values = new ArrayList<>();
        for (List<Source> combo : group) {
            Value value = eval(arg, combo);
            if (!value.isNull()) {
                values.add(value);
            }
        }
        return switch (func.name()) {
            case "COUNT" -> new Value.LongVal(values.size());
            case "SUM" -> sum(values);
            case "AVG" -> values.isEmpty() ? Value.NULL
                    : new Value.DoubleVal(values.stream().mapToDouble(Values::asDouble).sum() / values.size());
            case "MIN" -> reduce(values, true);
            case "MAX" -> reduce(values, false);
            default -> throw new IllegalArgumentException("unknown function: " + func.name());
        };
    }

    private Value sum(List<Value> values) {
        if (values.isEmpty()) {
            return Value.NULL;
        }
        boolean integral = values.stream().allMatch(v -> v instanceof Value.IntVal || v instanceof Value.LongVal);
        double total = values.stream().mapToDouble(Values::asDouble).sum();
        return integral ? new Value.LongVal((long) total) : new Value.DoubleVal(total);
    }

    private Value reduce(List<Value> values, boolean min) {
        if (values.isEmpty()) {
            return Value.NULL;
        }
        Value best = values.getFirst();
        for (Value value : values) {
            int cmp = Values.compare(value, best);
            if ((min && cmp < 0) || (!min && cmp > 0)) {
                best = value;
            }
        }
        return best;
    }

    private Value evalLogical(Expr.Logical logical, List<Source> sources) {
        boolean left = Values.truthy(eval(logical.left(), sources));
        if (logical.op().equals("AND")) {
            return new Value.BoolVal(left && Values.truthy(eval(logical.right(), sources)));
        }
        return new Value.BoolVal(left || Values.truthy(eval(logical.right(), sources)));
    }

    private Value compareValues(String op, Value left, Value right) {
        if (left.isNull() || right.isNull()) {
            return new Value.BoolVal(false);
        }
        int cmp = Values.compare(left, right);
        boolean result = switch (op) {
            case "=" -> cmp == 0;
            case "<>", "!=" -> cmp != 0;
            case "<" -> cmp < 0;
            case ">" -> cmp > 0;
            case "<=" -> cmp <= 0;
            case ">=" -> cmp >= 0;
            default -> throw new IllegalArgumentException("unknown operator: " + op);
        };
        return new Value.BoolVal(result);
    }

    private Value lookup(Expr.ColumnRef ref, List<Source> sources) {
        if (ref.table() != null) {
            for (Source source : sources) {
                if (matchesAlias(source, ref.table())) {
                    int index = source.schema().indexOf(ref.name());
                    if (index < 0) {
                        throw new IllegalArgumentException("unknown column: " + ref.table() + "." + ref.name());
                    }
                    return source.row().get(index);
                }
            }
            throw new IllegalArgumentException("unknown table or alias: " + ref.table());
        }
        Value found = null;
        int matches = 0;
        for (Source source : sources) {
            int index = source.schema().indexOf(ref.name());
            if (index >= 0) {
                found = source.row().get(index);
                matches++;
            }
        }
        if (matches == 0) {
            throw new IllegalArgumentException("unknown column: " + ref.name());
        }
        if (matches > 1) {
            throw new IllegalArgumentException("ambiguous column: " + ref.name());
        }
        return found;
    }

    private boolean matchesAlias(Source source, String name) {
        return source.alias().equalsIgnoreCase(name) || source.schema().name().equalsIgnoreCase(name);
    }

    private List<String> starColumns(List<Source> layout) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Source source : layout) {
            for (String column : source.schema().columnNames()) {
                counts.merge(column.toLowerCase(), 1, Integer::sum);
            }
        }
        List<String> columns = new ArrayList<>();
        for (Source source : layout) {
            for (String column : source.schema().columnNames()) {
                if (counts.get(column.toLowerCase()) > 1) {
                    columns.add(source.alias() + "." + column);
                } else {
                    columns.add(column);
                }
            }
        }
        return columns;
    }

    private String columnName(Statement.SelectItem item) {
        if (item.alias() != null) {
            return item.alias();
        }
        return nameOf(item.expr());
    }

    private String nameOf(Expr expr) {
        return switch (expr) {
            case Expr.ColumnRef ref -> ref.table() == null ? ref.name() : ref.table() + "." + ref.name();
            case Expr.Lit lit -> Values.render(lit.value());
            case Expr.Func func -> func.name().toLowerCase() + "(" + (func.star() ? "*" : nameOf(func.args().getFirst())) + ")";
            case Expr.Arith arith -> nameOf(arith.left()) + " " + arith.op() + " " + nameOf(arith.right());
            default -> "expr";
        };
    }

    private Expr columnRef(String column) {
        int dot = column.indexOf('.');
        if (dot >= 0) {
            return new Expr.ColumnRef(column.substring(0, dot), column.substring(dot + 1));
        }
        return new Expr.ColumnRef(null, column);
    }

    private int indexOfColumn(List<String> columns, String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        String simple = name.contains(".") ? name.substring(name.indexOf('.') + 1) : name;
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).equalsIgnoreCase(simple)) {
                return i;
            }
        }
        return -1;
    }

    private String aliasOf(String alias, String table) {
        return alias != null ? alias : table;
    }
}
