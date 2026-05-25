package com.javadb.server;

import com.javadb.engine.Database;
import com.javadb.engine.QueryResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.stream.Collectors;

public final class Session implements Runnable {

    private final Socket socket;
    private final Database db;

    public Session(Socket socket, Database db) {
        this.socket = socket;
        this.db = db;
    }

    @Override
    public void run() {
        try (socket; InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
            while (true) {
                String sql = Protocol.readMessage(in);
                if (sql == null) {
                    return;
                }
                Protocol.writeMessage(out, handle(sql));
            }
        } catch (IOException ignored) {
        }
    }

    private String handle(String sql) {
        try {
            List<QueryResult> results = db.execute(sql);
            if (results.isEmpty()) {
                return "OK";
            }
            return results.stream().map(QueryResult::format).collect(Collectors.joining("\n\n"));
        } catch (RuntimeException e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
