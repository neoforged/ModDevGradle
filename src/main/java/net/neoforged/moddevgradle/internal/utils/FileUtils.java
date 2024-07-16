package net.neoforged.moddevgradle.internal.utils;

import org.jetbrains.annotations.ApiStatus;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@ApiStatus.Internal
public final class FileUtils {
    /**
     * The maximum number of tries that the system will try to atomically move a file.
     */
    private static final int MAX_TRIES = 2;

    private FileUtils() {
    }

    public static void writeStringSafe(final Path destination, final String content, final Charset charset) throws IOException {
        if (!charset.newEncoder().canEncode(content)) {
            throw new IllegalArgumentException("The given character set " + charset
                                               + " cannot represent this string: " + content);
        }

        try (final OutputStream out = newSafeFileOutputStream(destination)) {
            final byte[] encodedContent = content.getBytes(charset);
            out.write(encodedContent);
        }
    }

    public static void writeLinesSafe(final Path destination, final List<String> lines, final Charset charset) throws IOException {
        writeStringSafe(destination, String.join("\n", lines), charset);
    }

    public static OutputStream newSafeFileOutputStream(final Path destination) throws IOException {
        final String uniqueId = ProcessHandle.current().pid() + "." + Thread.currentThread().getId();

        final Path tempFile = destination.resolveSibling(destination.getFileName().toString() + "." + uniqueId + ".tmp");
        final boolean[] closed = new boolean[1];
        return new FilterOutputStream(Files.newOutputStream(tempFile)) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                    if (!closed[0]) {
                        atomicMoveIfPossible(tempFile, destination);
                    }
                } finally {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (final IOException ignored) {
                    }
                    closed[0] = true;
                }
            }
        };
    }

    /**
     * Atomically moves the given source file to the given destination file.
     *
     * @param source      The source file
     * @param destination The destination file
     * @throws IOException If an I/O error occurs
     */
    @SuppressWarnings("BusyWait")

    public static void atomicMove(final Path source, final Path destination) throws IOException {
        if (Files.isDirectory(destination)) {
            throw new IOException("Cannot overwrite directory " + destination);
        }

        try {
            atomicMoveIfPossible(source, destination);
        } catch (final AccessDeniedException ex) {
            // Sometimes because of file locking this will fail... Let's just try again and hope for the best
            // Thanks Windows!
            for (int tries = 0; true; ++tries) {
                // Pause for a bit
                try {
                    Thread.sleep(10L * tries);
                    atomicMoveIfPossible(source, destination);
                    return;
                } catch (final AccessDeniedException ex2) {
                    if (tries == MAX_TRIES - 1) {
                        throw ex;
                    }
                } catch (final InterruptedException exInterrupt) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }

    /**
     * Atomically moves the given source file to the given destination file.
     * If the atomic move is not supported, the file will be moved normally.
     *
     * @param source      The source file
     * @param destination The destination file
     * @throws IOException If an I/O error occurs
     */
    private static void atomicMoveIfPossible(final Path source, final Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException ex) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
