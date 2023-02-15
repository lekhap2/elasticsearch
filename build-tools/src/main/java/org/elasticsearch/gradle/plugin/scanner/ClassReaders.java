/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.plugin.scanner;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A utility class containing methods to create streams of ASM's ClassReader
 *
 * @see ClassReader
 */
public class ClassReaders {
    private static final String MODULE_INFO = "module-info.class";

    /**
     * This method must be used within a try-with-resources statement or similar
     * control structure.
     */
    public static List<ClassReader> ofDirWithJars(Path dir) {
        if (dir == null) {
            return Collections.emptyList();
        }
        try (var stream = Files.list(dir)) {
            return ofPaths(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<ClassReader> ofPaths(Stream<Path> list) {
        return list.filter(Files::exists).flatMap(p -> {
            if (p.toString().endsWith(".jar")) {
                return classesInJar(p).stream();
            } else {
                return classesInPath(p).stream();
            }
        }).toList();
    }

    private static List<ClassReader> classesInJar(Path jar) {
        try (FileSystem jarFs = FileSystems.newFileSystem(jar)) {
            Path root = jarFs.getPath("/");
            return classesInPath(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<ClassReader> classesInPath(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(p -> p.toString().endsWith(".class"))
                .filter(p -> p.toString().endsWith(MODULE_INFO) == false)
                .filter(p -> p.toString().startsWith("/META-INF") == false)// skip multi-release files
                .map(p -> {
                    try (InputStream is = Files.newInputStream(p)) {
                        byte[] classBytes = is.readAllBytes();
                        return new ClassReader(classBytes);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                })
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}