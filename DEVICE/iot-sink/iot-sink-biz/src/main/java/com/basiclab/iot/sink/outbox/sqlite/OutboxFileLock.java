package com.basiclab.iot.sink.outbox.sqlite;

import java.nio.channels.FileChannel;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;

/**
 * TD-002 §5 单实例文件锁（FileChannel.tryLock，防多进程并发写同一 outbox）。
 */
public final class OutboxFileLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;

    public OutboxFileLock(Path lockFile) throws IOException {
        FileChannel opened = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        FileLock acquired = null;
        try {
            acquired = opened.tryLock();
            if (acquired == null) {
                throw alreadyOwned(lockFile, null);
            }
        } catch (OverlappingFileLockException e) {
            IllegalStateException failure = alreadyOwned(lockFile, e);
            closeAfterFailedAcquire(opened, failure);
            throw failure;
        } catch (IOException e) {
            closeAfterFailedAcquire(opened, e);
            throw e;
        } catch (RuntimeException | Error e) {
            closeAfterFailedAcquire(opened, e);
            throw e;
        }
        this.channel = opened;
        this.lock = acquired;
    }

    private static void closeAfterFailedAcquire(FileChannel opened, Throwable failure) {
        try {
            opened.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static IllegalStateException alreadyOwned(Path lockFile, Throwable cause) {
        String message = "OUTBOX_ALREADY_OWNED: another process or thread holds " + lockFile;
        return cause == null ? new IllegalStateException(message)
                : new IllegalStateException(message, cause);
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (IOException e) {
            failure = e;
        } finally {
            try {
                channel.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
