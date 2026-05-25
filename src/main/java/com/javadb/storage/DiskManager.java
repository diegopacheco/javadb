package com.javadb.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public final class DiskManager implements AutoCloseable {

    private final Path directory;
    private final Map<String, FileChannel> channels = new HashMap<>();

    public DiskManager(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FileChannel channel(String table) {
        return channels.computeIfAbsent(table, this::open);
    }

    private FileChannel open(String table) {
        try {
            Path file = directory.resolve(table + ".tbl");
            return FileChannel.open(file, StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int numPages(String table) {
        try {
            return (int) (channel(table).size() / Page.PAGE_SIZE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void readPage(PageId id, Page page) {
        try {
            FileChannel channel = channel(id.table());
            ByteBuffer buffer = page.segment().asByteBuffer();
            buffer.clear();
            long offset = (long) id.pageNo() * Page.PAGE_SIZE;
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer, offset + buffer.position());
                if (read < 0) {
                    break;
                }
            }
            while (buffer.hasRemaining()) {
                buffer.put((byte) 0);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writePage(PageId id, Page page) {
        try {
            FileChannel channel = channel(id.table());
            ByteBuffer buffer = page.segment().asByteBuffer();
            buffer.clear();
            long offset = (long) id.pageNo() * Page.PAGE_SIZE;
            while (buffer.hasRemaining()) {
                channel.write(buffer, offset + buffer.position());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int allocatePage(String table) {
        int pageNo = numPages(table);
        try {
            FileChannel channel = channel(table);
            ByteBuffer zero = ByteBuffer.allocate(Page.PAGE_SIZE);
            long offset = (long) pageNo * Page.PAGE_SIZE;
            while (zero.hasRemaining()) {
                channel.write(zero, offset + zero.position());
            }
            return pageNo;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void force() {
        try {
            for (FileChannel channel : channels.values()) {
                channel.force(true);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            for (FileChannel channel : channels.values()) {
                channel.close();
            }
            channels.clear();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
