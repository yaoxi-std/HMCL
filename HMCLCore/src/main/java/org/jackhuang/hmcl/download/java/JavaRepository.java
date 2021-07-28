/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 * Copyright (C) 2021  yaoxi-std  <yaoxi20061225@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.download.java;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class JavaRepository {
    private JavaRepository() {
    }

    public static Task<?> downloadJava(GameJavaVersion javaVersion, DownloadProvider downloadProvider) {
        return new JavaDownloadTask(javaVersion, getJavaStoragePath(), downloadProvider)
                .thenRunAsync(() -> {
                    Optional<String> platform = getCurrentJavaPlatform();
                    if (platform.isPresent()) {
                        addJava(getJavaHome(javaVersion, platform.get()));
                    }
                });
    }

    public static void addJava(Path javaHome) throws InterruptedException, IOException {
        if (Files.isDirectory(javaHome)) {
            Path executable = JavaVersion.getExecutable(javaHome);
            if (Files.isRegularFile(executable)) {
                JavaVersion.getJavas().add(JavaVersion.fromExecutable(executable));
            }
        }
    }

    public static void initialize() throws IOException, InterruptedException {
        Optional<String> platformOptional = getCurrentJavaPlatform();
        if (platformOptional.isPresent()) {
            String platform = platformOptional.get();
            Path javaStoragePath = getJavaStoragePath();
            if (Files.isDirectory(javaStoragePath)) {
                try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(javaStoragePath)) {
                    for (Path component : dirStream) {
                        Path javaHome = component.resolve(platform).resolve(component.getFileName());
                        try {
                            addJava(javaHome);
                        } catch (IOException e) {
                            LOG.log(Level.WARNING, "Failed to determine Java at " + javaHome, e);
                        }
                    }
                }
            }
        }
    }

    public static Optional<String> getCurrentJavaPlatform() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
            if (Architecture.CURRENT == Architecture.X86) {
                return Optional.of("linux-i386");
            } else if (Architecture.CURRENT == Architecture.X86_64) {
                return Optional.of("linux");
            }
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
            if (Architecture.CURRENT == Architecture.X86_64) {
                return Optional.of("mac-os");
            } else if (Architecture.CURRENT == Architecture.ARM64) {
                return Optional.of("mac-os");
            }
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            if (Architecture.CURRENT == Architecture.X86) {
                return Optional.of("windows-x86");
            } else if (Architecture.CURRENT == Architecture.X86_64) {
                return Optional.of("windows-x64");
            }
        }
        return Optional.empty();
    }

    public static Path getJavaStoragePath() {
        return CacheRepository.getInstance().getCacheDirectory().resolve("java");
    }

    public static Path getJavaHome(GameJavaVersion javaVersion, String platform) {
        return getJavaStoragePath().resolve(javaVersion.getComponent()).resolve(platform).resolve(javaVersion.getComponent());
    }
}
