package com.javadb.engine;

import com.javadb.types.Values;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTest {

    @TempDir
    Path dir;
    private Database db;

    @BeforeEach
    void open() {
        db = new Database(dir);
    }

    @AfterEach
    void close() {
        db.close();
    }

    private QueryResult exec(String sql) {
        return db.execute(sql).getLast();
    }

    private String cell(QueryResult result, int row, int column) {
        return Values.render(result.rows().get(row).get(column));
    }

    @Test
    void insertsAndSelectsAllColumns() {
        exec("CREATE TABLE users (id INT, name TEXT, age INT)");
        exec("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25)");
        QueryResult result = exec("SELECT * FROM users");
        assertEquals(java.util.List.of("id", "name", "age"), result.columns());
        assertEquals(2, result.rows().size());
    }

    @Test
    void filtersRowsWithWhere() {
        exec("CREATE TABLE users (id INT, name TEXT, age INT)");
        exec("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Cara', 41)");
        QueryResult result = exec("SELECT name FROM users WHERE age > 28");
        assertEquals(2, result.rows().size());
    }

    @Test
    void computesCountAndAverage() {
        exec("CREATE TABLE users (id INT, name TEXT, age INT)");
        exec("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 20)");
        QueryResult result = exec("SELECT COUNT(*), AVG(age) FROM users");
        assertEquals(1, result.rows().size());
        assertEquals("2", cell(result, 0, 0));
        assertEquals("25.0", cell(result, 0, 1));
    }

    @Test
    void groupsAndOrders() {
        exec("CREATE TABLE sales (dept TEXT, amount INT)");
        exec("INSERT INTO sales VALUES ('a', 10), ('b', 5), ('a', 7), ('b', 3)");
        QueryResult result = exec("SELECT dept, SUM(amount) FROM sales GROUP BY dept ORDER BY dept");
        assertEquals(2, result.rows().size());
        assertEquals("a", cell(result, 0, 0));
        assertEquals("17", cell(result, 0, 1));
        assertEquals("b", cell(result, 1, 0));
        assertEquals("8", cell(result, 1, 1));
    }

    @Test
    void joinsTwoTables() {
        exec("CREATE TABLE users (id INT, name TEXT)");
        exec("CREATE TABLE orders (uid INT, item TEXT)");
        exec("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");
        exec("INSERT INTO orders VALUES (1, 'book'), (1, 'pen'), (2, 'cup')");
        QueryResult result = exec("SELECT name, item FROM users JOIN orders ON id = uid ORDER BY item");
        assertEquals(3, result.rows().size());
        assertEquals("book", cell(result, 0, 1));
        assertEquals("Alice", cell(result, 0, 0));
    }

    @Test
    void updatesMatchingRows() {
        exec("CREATE TABLE users (id INT, name TEXT)");
        exec("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");
        QueryResult update = exec("UPDATE users SET name = 'Alicia' WHERE id = 1");
        assertEquals("UPDATE 1", update.message());
        QueryResult result = exec("SELECT name FROM users WHERE id = 1");
        assertEquals("Alicia", cell(result, 0, 0));
    }

    @Test
    void deletesMatchingRows() {
        exec("CREATE TABLE users (id INT, name TEXT)");
        exec("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");
        exec("DELETE FROM users WHERE id = 2");
        QueryResult result = exec("SELECT COUNT(*) FROM users");
        assertEquals("1", cell(result, 0, 0));
    }

    @Test
    void indexedEqualityReturnsTheRow() {
        exec("CREATE TABLE users (id INT, name TEXT)");
        exec("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob'), (3, 'Cara')");
        exec("CREATE INDEX users_id ON users (id)");
        QueryResult result = exec("SELECT name FROM users WHERE id = 2");
        assertEquals(1, result.rows().size());
        assertEquals("Bob", cell(result, 0, 0));
    }

    @Test
    void indexStaysConsistentAfterUpdate() {
        exec("CREATE TABLE users (id INT, name TEXT)");
        exec("INSERT INTO users VALUES (1, 'Alice')");
        exec("CREATE INDEX users_id ON users (id)");
        exec("UPDATE users SET id = 9 WHERE id = 1");
        assertTrue(exec("SELECT name FROM users WHERE id = 1").rows().isEmpty());
        assertEquals("Alice", cell(exec("SELECT name FROM users WHERE id = 9"), 0, 0));
    }

    @Test
    void committedRowsSurviveReopen() {
        exec("CREATE TABLE users (id INT, name TEXT)");
        exec("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");
        db.close();
        db = new Database(dir);
        QueryResult result = exec("SELECT COUNT(*) FROM users");
        assertEquals("2", cell(result, 0, 0));
    }
}
