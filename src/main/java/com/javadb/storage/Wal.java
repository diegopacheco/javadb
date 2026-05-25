package com.javadb.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class Wal implements AutoCloseable {

    private final FileChannel channel;

    public Wal(Path directory) {
        try {
            Path file = directory.resolve("wal.log");
            this.channel = FileChannel.open(file, StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void append(PageId id, Page page) {
        try {
            byte[] name = id.table().getBytes(StandardCharsets.UTF_8);
            ByteBuffer header = ByteBuffer.allocate(4 + name.length + 4 + 4);
            header.putInt(name.length);
            header.put(name);
            header.putInt(id.pageNo());
            header.putInt(Page.PAGE_SIZE);
            header.flip();
            writeFully(header, channel.size());

            ByteBuffer image = page.segment().asByteBuffer();
            image.clear();
            writeFully(image, channel.size());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void force() {
        try {
            channel.force(true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void truncate() {
        try {
            channel.truncate(0);
            channel.force(true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void recover(DiskManager disk) {
        try (Arena arena = Arena.ofConfined()) {
            long size = channel.size();
            if (size == 0) {
                return;
            }
            long position = 0;
            MemorySegment scratch = arena.allocate(Page.PAGE_SIZE);
            Page page = new Page(scratch);
            while (position < size) {
                if (size - position < 4) {
                    break;
                }
                ByteBuffer lenBuffer = ByteBuffer.allocate(4);
                position += readFully(lenBuffer, position);
                lenBuffer.flip();
                int nameLength = lenBuffer.getInt();
                if (size - position < (long) nameLength + 8) {
                    break;
                }

                ByteBuffer nameBuffer = ByteBuffer.allocate(nameLength);
                position += readFully(nameBuffer, position);
                nameBuffer.flip();
                String table = StandardCharsets.UTF_8.decode(nameBuffer).toString();

                ByteBuffer metaBuffer = ByteBuffer.allocate(8);
                position += readFully(metaBuffer, position);
                metaBuffer.flip();
                int pageNo = metaBuffer.getInt();
                int imageLength = metaBuffer.getInt();
                if (size - position < imageLength) {
                    break;
                }

                ByteBuffer imageBuffer = scratch.asByteBuffer();
                imageBuffer.clear();
                imageBuffer.limit(imageLength);
                position += readFully(imageBuffer, position);

                disk.writePage(new PageId(table, pageNo), page);
            }
            disk.force();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        truncate();
    }

    private void writeFully(ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer, position + buffer.position());
        }
    }

    private int readFully(ByteBuffer buffer, long position) throws IOException {
        int total = 0;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position + buffer.position());
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    @Override
    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
