package com.javadb.sql;

import java.util.ArrayList;
import java.util.List;

public final class Lexer {

    private final String input;
    private int pos;

    public Lexer(String input) {
        this.input = input;
    }

    public static List<Token> tokenize(String input) {
        return new Lexer(input).scan();
    }

    public List<Token> scan() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            skipWhitespace();
            if (pos >= input.length()) {
                tokens.add(new Token(Token.Kind.EOF, ""));
                return tokens;
            }
            char c = input.charAt(pos);
            if (c == '\'') {
                tokens.add(readString());
            } else if (Character.isDigit(c)) {
                tokens.add(readNumber());
            } else if (Character.isLetter(c) || c == '_') {
                tokens.add(readWord());
            } else {
                tokens.add(readSymbol());
            }
        }
    }

    private void skipWhitespace() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '-' && pos + 1 < input.length() && input.charAt(pos + 1) == '-') {
                while (pos < input.length() && input.charAt(pos) != '\n') {
                    pos++;
                }
            } else if (Character.isWhitespace(c)) {
                pos++;
            } else {
                return;
            }
        }
    }

    private Token readString() {
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '\'') {
                if (pos + 1 < input.length() && input.charAt(pos + 1) == '\'') {
                    sb.append('\'');
                    pos += 2;
                    continue;
                }
                pos++;
                return new Token(Token.Kind.STRING, sb.toString());
            }
            sb.append(c);
            pos++;
        }
        throw new IllegalArgumentException("unterminated string literal");
    }

    private Token readNumber() {
        int start = pos;
        boolean dot = false;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isDigit(c)) {
                pos++;
            } else if (c == '.' && !dot) {
                dot = true;
                pos++;
            } else {
                break;
            }
        }
        return new Token(Token.Kind.NUMBER, input.substring(start, pos));
    }

    private Token readWord() {
        int start = pos;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '_') {
                pos++;
            } else {
                break;
            }
        }
        return new Token(Token.Kind.WORD, input.substring(start, pos));
    }

    private Token readSymbol() {
        char c = input.charAt(pos);
        if (c == '<' && peek(1) == '>') {
            pos += 2;
            return new Token(Token.Kind.SYMBOL, "<>");
        }
        if (c == '<' && peek(1) == '=') {
            pos += 2;
            return new Token(Token.Kind.SYMBOL, "<=");
        }
        if (c == '>' && peek(1) == '=') {
            pos += 2;
            return new Token(Token.Kind.SYMBOL, ">=");
        }
        if (c == '!' && peek(1) == '=') {
            pos += 2;
            return new Token(Token.Kind.SYMBOL, "!=");
        }
        if ("()=,.*;+-/<>".indexOf(c) >= 0) {
            pos++;
            return new Token(Token.Kind.SYMBOL, String.valueOf(c));
        }
        throw new IllegalArgumentException("unexpected character: " + c);
    }

    private char peek(int ahead) {
        int i = pos + ahead;
        return i < input.length() ? input.charAt(i) : '\0';
    }
}
