package com.javadb.client;

import com.javadb.server.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class SqlClient {

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6543;

        Socket connection;
        try {
            connection = new Socket(host, port);
        } catch (ConnectException e) {
            System.err.println("cannot reach javadb at " + host + ":" + port);
            System.err.println("start the server first: ./run.sh " + port);
            return;
        }

        try (Socket socket = connection;
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream();
             BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            System.out.println("connected to javadb at " + host + ":" + port);
            System.out.println("type SQL ending with ';'. type 'exit' to quit.");

            StringBuilder pending = new StringBuilder();
            System.out.print("javadb> ");
            String line;
            while ((line = console.readLine()) != null) {
                String trimmed = line.trim();
                if (pending.isEmpty() && (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit"))) {
                    return;
                }
                pending.append(line).append('\n');
                if (trimmed.endsWith(";")) {
                    Protocol.writeMessage(out, pending.toString());
                    pending.setLength(0);
                    String response = Protocol.readMessage(in);
                    if (response == null) {
                        System.out.println("connection closed by server");
                        return;
                    }
                    System.out.println(response);
                    System.out.print("javadb> ");
                } else {
                    System.out.print("    ...> ");
                }
            }
        }
    }
}
