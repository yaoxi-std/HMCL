/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.FileDownloadTask.IntegrityCheck;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CacheRepository;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Task to download Minecraft jar
 * @author huangyuhui
 */
public final class GameDownloadTask extends Task<Void> {
    private final DefaultDependencyManager dependencyManager;
    private final String gameVersion;
    private final Version version;
    private final List<Task<?>> dependencies = new LinkedList<>();
    private final boolean isClient;

    public GameDownloadTask(DefaultDependencyManager dependencyManager, String gameVersion, Version version) {
        this(dependencyManager, gameVersion, version, true);
    }

    public GameDownloadTask(DefaultDependencyManager dependencyManager, String gameVersion, Version version, boolean isClient) {
        this.dependencyManager = dependencyManager;
        this.gameVersion = gameVersion;
        this.version = version.resolve(dependencyManager.getGameRepository());
        this.isClient = isClient;

        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public void execute() {
        DefaultGameRepository repository = dependencyManager.getGameRepository();
        File jar = isClient ? repository.getClientJar(version) : repository.getServerJar(version);

        FileDownloadTask task = new FileDownloadTask(
                dependencyManager.getDownloadProvider().injectURLWithCandidates(version.getDownloadInfo(isClient).getUrl()),
                jar,
                IntegrityCheck.of(CacheRepository.SHA1, version.getDownloadInfo(isClient).getSha1()));
        task.setCaching(true);
        task.setCacheRepository(dependencyManager.getCacheRepository());

        if (gameVersion != null)
            task.setCandidate(dependencyManager.getCacheRepository().getCommonDirectory().resolve("jars").resolve(gameVersion + ".jar"));

        dependencies.add(task);
    }
    
}
