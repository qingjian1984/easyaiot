package com.basiclab.iot.sink.outbox.sqlite;

import java.nio.channels.FileChannel;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * TD-002 §5 单实例文件锁（FileChannel.tryLock，防多进程并发写同一 outbox）。
 */
public final class OutboxFileLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;

    public OutboxFileLock(Path lockFile) throws IOException {
        this.channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        this.lock = channel.tryLock();
        if (lock == null) {
            channel.close();
            throw new IllegalStateException("OUTBOX_LOCK_HELD: another process holds " + lockFile);
        }
    }

    @Override
    public void close() throws IOException {
        if (lock != null) {
            lock.release();
        }
        channel.close();
    }
}
