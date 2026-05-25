package com.javadb.sql.ast;

import com.javadb.types.Value;

import java.util.List;

public sealed interface Expr
        permits Expr.ColumnRef, Expr.Lit, Expr.Arith, Expr.Compare,
                Expr.Logical, Expr.Not, Expr.Func, Expr.Star {

    record ColumnRef(String table, String name) implements Expr {}

    record Lit(Value value) implements Expr {}

    record Arith(char op, Expr left, Expr right) implements Expr {}

    record Compare(String op, Expr left, Expr right) implements Expr {}

    record Logical(String op, Expr left, Expr right) implements Expr {}

    record Not(Expr inner) implements Expr {}

    record Func(String name, List<Expr> args, boolean star) implements Expr {}

    record Star() implements Expr {}
}
