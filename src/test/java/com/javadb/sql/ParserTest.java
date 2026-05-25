package com.javadb.sql;

import com.javadb.sql.ast.Expr;
import com.javadb.sql.ast.Statement;
import com.javadb.types.DataType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserTest {

    @Test
    void parsesCreateTableWithTypedColumns() {
        Statement statement = Parser.parse("CREATE TABLE users (id INT, name TEXT)").getFirst();
        Statement.CreateTable create = assertInstanceOf(Statement.CreateTable.class, statement);
        assertEquals("users", create.table());
        assertEquals(DataType.INT, create.columns().get(0).type());
        assertEquals(DataType.TEXT, create.columns().get(1).type());
    }

    @Test
    void parsesInsertWithMultipleRows() {
        Statement statement = Parser.parse("INSERT INTO t VALUES (1, 'a'), (2, 'b')").getFirst();
        Statement.Insert insert = assertInstanceOf(Statement.Insert.class, statement);
        assertNull(insert.columns());
        assertEquals(2, insert.rows().size());
    }

    @Test
    void parsesSelectWithJoinWhereGroupAndOrder() {
        String sql = "SELECT u.name, SUM(o.total) FROM users u "
                + "JOIN orders o ON u.id = o.uid WHERE o.total > 10 "
                + "GROUP BY u.name ORDER BY u.name DESC";
        Statement.Select select = assertInstanceOf(Statement.Select.class, Parser.parse(sql).getFirst());
        assertEquals("u", select.alias());
        assertEquals(1, select.joins().size());
        assertEquals("orders", select.joins().getFirst().table());
        assertEquals(List.of("u.name"), select.groupBy());
        assertEquals(1, select.orderBy().size());
        assertTrue(!select.orderBy().getFirst().ascending());
    }

    @Test
    void respectsOperatorPrecedence() {
        Statement.Select select = (Statement.Select) Parser.parse("SELECT 1 FROM t WHERE a = 2 + 3 * 4").getFirst();
        Expr.Compare compare = assertInstanceOf(Expr.Compare.class, select.where());
        Expr.Arith add = assertInstanceOf(Expr.Arith.class, compare.right());
        assertEquals('+', add.op());
        assertInstanceOf(Expr.Arith.class, add.right());
    }

    @Test
    void parsesSeveralStatementsSeparatedBySemicolons() {
        List<Statement> statements = Parser.parse("CREATE TABLE t (id INT); INSERT INTO t VALUES (1);");
        assertEquals(2, statements.size());
    }
}
