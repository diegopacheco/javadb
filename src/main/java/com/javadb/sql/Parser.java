package com.javadb.sql;

import com.javadb.catalog.Column;
import com.javadb.sql.ast.Expr;
import com.javadb.sql.ast.Statement;
import com.javadb.types.DataType;
import com.javadb.types.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Parser {

    private static final Set<String> RESERVED = Set.of(
            "FROM", "JOIN", "INNER", "WHERE", "GROUP", "ORDER", "ON", "AS",
            "VALUES", "SET", "BY", "AND", "OR", "NOT", "ASC", "DESC");

    private final List<Token> tokens;
    private int pos;

    public Parser(String sql) {
        this.tokens = Lexer.tokenize(sql);
    }

    public static List<Statement> parse(String sql) {
        return new Parser(sql).parseAll();
    }

    public List<Statement> parseAll() {
        List<Statement> result = new ArrayList<>();
        while (!isEof()) {
            if (matchSymbol(";")) {
                continue;
            }
            result.add(parseStatement());
            if (!matchSymbol(";") && !isEof()) {
                throw error("expected ';' or end of input");
            }
        }
        return result;
    }

    private Statement parseStatement() {
        Token t = peek();
        if (t.isWord("CREATE")) {
            return parseCreate();
        }
        if (t.isWord("INSERT")) {
            return parseInsert();
        }
        if (t.isWord("SELECT")) {
            return parseSelect();
        }
        if (t.isWord("UPDATE")) {
            return parseUpdate();
        }
        if (t.isWord("DELETE")) {
            return parseDelete();
        }
        throw error("unknown statement: " + t.text());
    }

    private Statement parseCreate() {
        expectWord("CREATE");
        if (matchWord("TABLE")) {
            return parseCreateTable();
        }
        if (matchWord("INDEX")) {
            return parseCreateIndex();
        }
        throw error("expected TABLE or INDEX after CREATE");
    }

    private Statement parseCreateTable() {
        String name = ident();
        expectSymbol("(");
        List<Column> columns = new ArrayList<>();
        do {
            String columnName = ident();
            String typeName = ident();
            columns.add(new Column(columnName, DataType.from(typeName)));
        } while (matchSymbol(","));
        expectSymbol(")");
        return new Statement.CreateTable(name, columns);
    }

    private Statement parseCreateIndex() {
        String name = ident();
        expectWord("ON");
        String table = ident();
        expectSymbol("(");
        String column = ident();
        expectSymbol(")");
        return new Statement.CreateIndex(name, table, column);
    }

    private Statement parseInsert() {
        expectWord("INSERT");
        expectWord("INTO");
        String table = ident();
        List<String> columns = null;
        if (matchSymbol("(")) {
            columns = new ArrayList<>();
            do {
                columns.add(ident());
            } while (matchSymbol(","));
            expectSymbol(")");
        }
        expectWord("VALUES");
        List<List<Expr>> rows = new ArrayList<>();
        do {
            expectSymbol("(");
            List<Expr> values = new ArrayList<>();
            if (!peek().isSymbol(")")) {
                do {
                    values.add(parseExpr());
                } while (matchSymbol(","));
            }
            expectSymbol(")");
            rows.add(values);
        } while (matchSymbol(","));
        return new Statement.Insert(table, columns, rows);
    }

    private Statement parseSelect() {
        expectWord("SELECT");
        List<Statement.SelectItem> items = new ArrayList<>();
        do {
            if (peek().isSymbol("*") && !peekAheadIsDot()) {
                next();
                items.add(new Statement.SelectItem(new Expr.Star(), null));
            } else {
                Expr expr = parseExpr();
                String alias = optionalAlias();
                items.add(new Statement.SelectItem(expr, alias));
            }
        } while (matchSymbol(","));

        expectWord("FROM");
        String table = ident();
        String alias = optionalAlias();

        List<Statement.JoinClause> joins = new ArrayList<>();
        while (peek().isWord("JOIN") || peek().isWord("INNER")) {
            matchWord("INNER");
            expectWord("JOIN");
            String joinTable = ident();
            String joinAlias = optionalAlias();
            expectWord("ON");
            Expr on = parseExpr();
            joins.add(new Statement.JoinClause(joinTable, joinAlias, on));
        }

        Expr where = null;
        if (matchWord("WHERE")) {
            where = parseExpr();
        }

        List<String> groupBy = new ArrayList<>();
        if (matchWord("GROUP")) {
            expectWord("BY");
            do {
                groupBy.add(columnName());
            } while (matchSymbol(","));
        }

        List<Statement.OrderKey> orderBy = new ArrayList<>();
        if (matchWord("ORDER")) {
            expectWord("BY");
            do {
                String column = columnName();
                boolean ascending = true;
                if (matchWord("DESC")) {
                    ascending = false;
                } else {
                    matchWord("ASC");
                }
                orderBy.add(new Statement.OrderKey(column, ascending));
            } while (matchSymbol(","));
        }

        return new Statement.Select(items, table, alias, joins, where, groupBy, orderBy);
    }

    private Statement parseUpdate() {
        expectWord("UPDATE");
        String table = ident();
        expectWord("SET");
        List<Statement.Assignment> assignments = new ArrayList<>();
        do {
            String column = ident();
            expectSymbol("=");
            Expr value = parseExpr();
            assignments.add(new Statement.Assignment(column, value));
        } while (matchSymbol(","));
        Expr where = null;
        if (matchWord("WHERE")) {
            where = parseExpr();
        }
        return new Statement.Update(table, assignments, where);
    }

    private Statement parseDelete() {
        expectWord("DELETE");
        expectWord("FROM");
        String table = ident();
        Expr where = null;
        if (matchWord("WHERE")) {
            where = parseExpr();
        }
        return new Statement.Delete(table, where);
    }

    private Expr parseExpr() {
        return parseOr();
    }

    private Expr parseOr() {
        Expr left = parseAnd();
        while (matchWord("OR")) {
            left = new Expr.Logical("OR", left, parseAnd());
        }
        return left;
    }

    private Expr parseAnd() {
        Expr left = parseNot();
        while (matchWord("AND")) {
            left = new Expr.Logical("AND", left, parseNot());
        }
        return left;
    }

    private Expr parseNot() {
        if (matchWord("NOT")) {
            return new Expr.Not(parseNot());
        }
        return parseComparison();
    }

    private Expr parseComparison() {
        Expr left = parseAdd();
        Token t = peek();
        if (t.kind() == Token.Kind.SYMBOL && isComparison(t.text())) {
            next();
            Expr right = parseAdd();
            return new Expr.Compare(t.text(), left, right);
        }
        return left;
    }

    private Expr parseAdd() {
        Expr left = parseMul();
        while (peek().isSymbol("+") || peek().isSymbol("-")) {
            char op = next().text().charAt(0);
            left = new Expr.Arith(op, left, parseMul());
        }
        return left;
    }

    private Expr parseMul() {
        Expr left = parseUnary();
        while (peek().isSymbol("*") || peek().isSymbol("/")) {
            char op = next().text().charAt(0);
            left = new Expr.Arith(op, left, parseUnary());
        }
        return left;
    }

    private Expr parseUnary() {
        if (matchSymbol("-")) {
            return new Expr.Arith('-', new Expr.Lit(new Value.IntVal(0)), parseUnary());
        }
        if (matchSymbol("+")) {
            return parseUnary();
        }
        return parsePrimary();
    }

    private Expr parsePrimary() {
        Token t = peek();
        if (t.kind() == Token.Kind.NUMBER) {
            next();
            return new Expr.Lit(numberValue(t.text()));
        }
        if (t.kind() == Token.Kind.STRING) {
            next();
            return new Expr.Lit(new Value.TextVal(t.text()));
        }
        if (t.isSymbol("(")) {
            next();
            Expr inner = parseExpr();
            expectSymbol(")");
            return inner;
        }
        if (t.kind() == Token.Kind.WORD) {
            if (t.isWord("TRUE")) {
                next();
                return new Expr.Lit(new Value.BoolVal(true));
            }
            if (t.isWord("FALSE")) {
                next();
                return new Expr.Lit(new Value.BoolVal(false));
            }
            if (t.isWord("NULL")) {
                next();
                return new Expr.Lit(Value.NULL);
            }
            String name = next().text();
            if (matchSymbol("(")) {
                return parseFunction(name);
            }
            if (matchSymbol(".")) {
                String column = ident();
                return new Expr.ColumnRef(name, column);
            }
            return new Expr.ColumnRef(null, name);
        }
        throw error("unexpected token: " + t.text());
    }

    private Expr parseFunction(String name) {
        if (matchSymbol("*")) {
            expectSymbol(")");
            return new Expr.Func(name.toUpperCase(), List.of(), true);
        }
        List<Expr> args = new ArrayList<>();
        if (!peek().isSymbol(")")) {
            do {
                args.add(parseExpr());
            } while (matchSymbol(","));
        }
        expectSymbol(")");
        return new Expr.Func(name.toUpperCase(), args, false);
    }

    private Value numberValue(String text) {
        if (text.indexOf('.') >= 0) {
            return new Value.DoubleVal(Double.parseDouble(text));
        }
        long value = Long.parseLong(text);
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return new Value.IntVal((int) value);
        }
        return new Value.LongVal(value);
    }

    private String columnName() {
        String first = ident();
        if (matchSymbol(".")) {
            return first + "." + ident();
        }
        return first;
    }

    private String optionalAlias() {
        if (matchWord("AS")) {
            return ident();
        }
        Token t = peek();
        if (t.kind() == Token.Kind.WORD && !RESERVED.contains(t.text().toUpperCase())) {
            return next().text();
        }
        return null;
    }

    private boolean peekAheadIsDot() {
        return false;
    }

    private boolean isComparison(String op) {
        return switch (op) {
            case "=", "<>", "!=", "<", ">", "<=", ">=" -> true;
            default -> false;
        };
    }

    private String ident() {
        Token t = peek();
        if (t.kind() != Token.Kind.WORD) {
            throw error("expected identifier but found '" + t.text() + "'");
        }
        return next().text();
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token next() {
        return tokens.get(pos++);
    }

    private boolean isEof() {
        return peek().kind() == Token.Kind.EOF;
    }

    private boolean matchWord(String word) {
        if (peek().isWord(word)) {
            pos++;
            return true;
        }
        return false;
    }

    private boolean matchSymbol(String symbol) {
        if (peek().isSymbol(symbol)) {
            pos++;
            return true;
        }
        return false;
    }

    private void expectWord(String word) {
        if (!matchWord(word)) {
            throw error("expected '" + word + "' but found '" + peek().text() + "'");
        }
    }

    private void expectSymbol(String symbol) {
        if (!matchSymbol(symbol)) {
            throw error("expected '" + symbol + "' but found '" + peek().text() + "'");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("parse error: " + message);
    }
}
