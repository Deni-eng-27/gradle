/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.fixtures.executer;

import com.google.common.io.ByteStreams;
import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.util.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

interface ClassPathMerger {
    ClassPathMerger INSTANCE = OperatingSystem.current().isWindows() ? new WindowsClassPathMerger() : new DoNothingClassPathMerger();
    // Actually 32KB, let's leave some margin
    int WINDOWS_CLASSPATH_LENGTH_LIMITATION = 1000;

    List<File> mergeClassPathIfNecessary(List<File> classPath);

    class WindowsClassPathMerger implements ClassPathMerger {
        @Override
        public List<File> mergeClassPathIfNecessary(List<File> classPath) {
            if (CollectionUtils.join(File.pathSeparator, classPath).length() < WINDOWS_CLASSPATH_LENGTH_LIMITATION) {
                return classPath;
            }
            return mergedClassPath(classPath);
        }

        private List<File> mergedClassPath(List<File> classPath) {
            try {
                String maneifestContent = generateManifestContent(classPath);
                File jar = jar(maneifestContent);
                return Collections.singletonList(jar);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        private File jar(String manifestContent) throws IOException {
            File jar = Files.createTempFile("classpath.jar", null).toFile();

            try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(jar))) {
                ZipEntry entry = new ZipEntry("META-INF/MANIFEST.MF");
                zipOutputStream.putNextEntry(entry);
                ByteStreams.copy(new ByteArrayInputStream(manifestContent.getBytes()), zipOutputStream);
                zipOutputStream.closeEntry();
            }
            return jar;
        }

        /**
         * Build the entry for classpath in jar MANIFEST.MF file, spaces in path should be escaped since
         * it's the delimiter.
         */
        private String generateManifestContent(List<File> classPath) throws IOException {
            List<URI> uri = CollectionUtils.collect(classPath, new Transformer<URI, File>() {
                @Override
                public URI transform(File file) {
                    return file.toURI();
                }
            });

            return make72Safe("Class-Path: " + CollectionUtils.join(" ", uri) + "\r\n");
        }

        private String make72Safe(String line) {
            StringBuilder result = new StringBuilder();
            int length = line.length();
            for (int i = 0; i < length; i += 69) {
                result.append(line, i, Math.min(i + 69, length));
                result.append("\r\n ");
            }
            return result.toString();
        }
    }

    class DoNothingClassPathMerger implements ClassPathMerger {
        @Override
        public List<File> mergeClassPathIfNecessary(List<File> classPath) {
            return classPath;
        }
    }
}