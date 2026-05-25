package com.javadb.sql;

public record Token(Token.Kind kind, String text) {

    public enum Kind {
        WORD,
        NUMBER,
        STRING,
        SYMBOL,
        EOF
    }

    public boolean isWord(String word) {
        return kind == Kind.WORD && text.equalsIgnoreCase(word);
    }

    public boolean isSymbol(String symbol) {
        return kind == Kind.SYMBOL && text.equals(symbol);
    }
}
