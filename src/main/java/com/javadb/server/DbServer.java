package com.javadb.server;

import com.javadb.engine.Database;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

public final class DbServer implements AutoCloseable {

    private final int port;
    private final Database db;
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public DbServer(int port, Path directory) {
        this.port = port;
        this.db = new Database(directory);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("javadb listening on port " + port);
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                Thread.ofVirtual().start(new Session(socket, db));
            } catch (IOException e) {
                if (running) {
                    throw e;
                }
            }
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        db.close();
    }
}
