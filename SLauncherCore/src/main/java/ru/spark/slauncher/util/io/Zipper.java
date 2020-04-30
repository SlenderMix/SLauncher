package ru.spark.slauncher.util.io;


import ru.spark.slauncher.util.function.ExceptionalPredicate;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Non thread-safe
 *
 * @author spark1337
 */
public final class Zipper implements Closeable {

    private final FileSystem fs;

    public Zipper(Path zipFile) throws IOException {
        this(zipFile, null);
    }

    public Zipper(Path zipFile, Charset encoding) throws IOException {
        Files.deleteIfExists(zipFile);
        fs = CompressingUtils.createWritableZipFileSystem(zipFile, encoding);
    }

    @Override
    public void close() throws IOException {
        fs.close();
    }

    /**
     * Compress all the files in sourceDir
     *
     * @param source  the file in basePath to be compressed
     * @param rootDir the path of the directory in this zip file.
     */
    public void putDirectory(Path source, String rootDir) throws IOException {
        putDirectory(source, rootDir, null);
    }

    /**
     * Compress all the files in sourceDir
     *
     * @param source    the file in basePath to be compressed
     * @param targetDir the path of the directory in this zip file.
     * @param filter    returns false if you do not want that file or directory
     */
    public void putDirectory(Path source, String targetDir, ExceptionalPredicate<String, IOException> filter) throws IOException {
        Path root = fs.getPath(targetDir);
        Files.createDirectories(root);
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (".DS_Store".equals(file.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                String relativePath = source.relativize(file).normalize().toString();
                if (filter != null && !filter.test(relativePath.replace('\\', '/'))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.copy(file, root.resolve(relativePath));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String relativePath = source.relativize(dir).normalize().toString();
                if (filter != null && !filter.test(relativePath.replace('\\', '/'))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path path = root.resolve(relativePath);
                if (Files.notExists(path)) {
                    Files.createDirectory(path);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void putFile(File file, String path) throws IOException {
        putFile(file.toPath(), path);
    }

    public void putFile(Path file, String path) throws IOException {
        Files.copy(file, fs.getPath(path));
    }

    public void putStream(InputStream in, String path) throws IOException {
        Files.copy(in, fs.getPath(path));
    }

    public void putTextFile(String text, String path) throws IOException {
        putTextFile(text, "UTF-8", path);
    }

    public void putTextFile(String text, String encoding, String pathName) throws IOException {
        Files.write(fs.getPath(pathName), text.getBytes(encoding));
    }

}
