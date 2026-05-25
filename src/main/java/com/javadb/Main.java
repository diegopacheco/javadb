package com.javadb;

import com.javadb.server.DbServer;

import java.nio.file.Path;

public final class Main {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 6543;
        Path directory = Path.of(args.length > 1 ? args[1] : "javadb-data");
        DbServer server = new DbServer(port, directory);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
    }
}
