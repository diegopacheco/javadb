package com.javadb.sql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LexerTest {

    @Test
    void splitsWordsNumbersStringsAndSymbols() {
        List<Token> tokens = Lexer.tokenize("SELECT id FROM t WHERE name = 'a''b'");
        assertEquals(Token.Kind.WORD, tokens.get(0).kind());
        assertEquals("SELECT", tokens.get(0).text());
        assertEquals(Token.Kind.STRING, tokens.get(7).kind());
        assertEquals("a'b", tokens.get(7).text());
    }

    @Test
    void recognizesMultiCharacterOperators() {
        List<Token> tokens = Lexer.tokenize("a <= b <> c >= d");
        assertEquals("<=", tokens.get(1).text());
        assertEquals("<>", tokens.get(3).text());
        assertEquals(">=", tokens.get(5).text());
    }

    @Test
    void keepsDecimalNumbersIntact() {
        List<Token> tokens = Lexer.tokenize("3.14");
        assertEquals(Token.Kind.NUMBER, tokens.get(0).kind());
        assertEquals("3.14", tokens.get(0).text());
    }

    @Test
    void ignoresLineComments() {
        List<Token> tokens = Lexer.tokenize("SELECT 1 -- a comment\n FROM t");
        assertEquals("SELECT", tokens.get(0).text());
        assertEquals("1", tokens.get(1).text());
        assertEquals("FROM", tokens.get(2).text());
    }
}
