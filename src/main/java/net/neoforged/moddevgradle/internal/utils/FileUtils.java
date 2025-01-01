package net.neoforged.moddevgradle.internal.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.Charset;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;
import org.gradle.api.GradleException;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class FileUtils {
    /**
     * The maximum number of tries that the system will try to atomically move a file.
     */
    private static final int MAX_TRIES = 2;

    private FileUtils() {}

    /**
     * Finds an explicitly defined Java module name in the given Jar file.
     */
    public static Optional<String> getExplicitJavaModuleName(File file) throws IOException {
        try (var jf = new JarFile(file, false, ZipFile.OPEN_READ, JarFile.runtimeVersion())) {
            var moduleInfoEntry = jf.getJarEntry("module-info.class");
            if (moduleInfoEntry != null) {
                try (var in = jf.getInputStream(moduleInfoEntry)) {
                    return Optional.of(ModuleDescriptor.read(in).name());
                }
            }

            var manifest = jf.getManifest();
            if (manifest == null) {
                return Optional.empty();
            }

            var automaticModuleName = manifest.getMainAttributes().getValue("Automatic-Module-Name");
            if (automaticModuleName == null) {
                return Optional.empty();
            }

            return Optional.of(automaticModuleName);
        } catch (Exception e) {
            throw new IOException("Failed to determine the Java module name of " + file + ": " + e, e);
        }
    }

    public static String hashFile(File file, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (var input = new DigestInputStream(new FileInputStream(file), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new GradleException("Failed to hash file " + file, e);
        }
    }

    public static void writeStringSafe(Path destination, String content, Charset charset) throws IOException {
        if (!charset.newEncoder().canEncode(content)) {
            throw new IllegalArgumentException("The given character set " + charset
                    + " cannot represent this string: " + content);
        }

        try (var out = newSafeFileOutputStream(destination)) {
            var encodedContent = content.getBytes(charset);
            out.write(encodedContent);
        }
    }

    public static void writeLinesSafe(Path destination, List<String> lines, Charset charset) throws IOException {
        writeStringSafe(destination, String.join("\n", lines), charset);
    }

    public static OutputStream newSafeFileOutputStream(Path destination) throws IOException {
        var uniqueId = ProcessHandle.current().pid() + "." + Thread.currentThread().getId();

        var tempFile = destination.resolveSibling(destination.getFileName().toString() + "." + uniqueId + ".tmp");
        var closed = new boolean[1];
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
                    } catch (IOException ignored) {}
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

    public static void atomicMove(Path source, Path destination) throws IOException {
        if (Files.isDirectory(destination)) {
            throw new IOException("Cannot overwrite directory " + destination);
        }

        try {
            atomicMoveIfPossible(source, destination);
        } catch (AccessDeniedException ex) {
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
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
