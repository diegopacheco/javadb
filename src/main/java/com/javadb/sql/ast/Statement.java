package com.javadb.sql.ast;

import com.javadb.catalog.Column;

import java.util.List;

public sealed interface Statement
        permits Statement.CreateTable, Statement.CreateIndex, Statement.Insert,
                Statement.Select, Statement.Update, Statement.Delete {

    record CreateTable(String table, List<Column> columns) implements Statement {}

    record CreateIndex(String name, String table, String column) implements Statement {}

    record Insert(String table, List<String> columns, List<List<Expr>> rows) implements Statement {}

    record SelectItem(Expr expr, String alias) {}

    record JoinClause(String table, String alias, Expr on) {}

    record OrderKey(String column, boolean ascending) {}

    record Select(List<SelectItem> items, String table, String alias,
                  List<JoinClause> joins, Expr where, List<String> groupBy,
                  List<OrderKey> orderBy) implements Statement {}

    record Assignment(String column, Expr value) {}

    record Update(String table, List<Assignment> assignments, Expr where) implements Statement {}

    record Delete(String table, Expr where) implements Statement {}
}
