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
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.MaintainTask;
import org.jackhuang.hmcl.download.game.VersionJsonSaveTask;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.GameJsonParseFailedEvent;
import org.jackhuang.hmcl.event.LoadedOneVersionEvent;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.event.RefreshingVersionsEvent;
import org.jackhuang.hmcl.event.RemoveVersionEvent;
import org.jackhuang.hmcl.event.RenameVersionEvent;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.Logging.LOG;

/**
 * An implementation of classic Minecraft game repository.
 *
 * @author huangyuhui
 */
public class DefaultGameRepository implements GameRepository {

    private File baseDirectory;
    protected Map<String, Version> versions;

    public DefaultGameRepository(File baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    public File getBaseDirectory() {
        return baseDirectory;
    }

    public void setBaseDirectory(File baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public boolean hasVersion(String id) {
        return id != null && versions.containsKey(id);
    }

    @Override
    public Version getVersion(String id) {
        if (!hasVersion(id))
            throw new VersionNotFoundException("Version '" + id + "' does not exist in " + versions.keySet() + ".");
        return versions.get(id);
    }

    @Override
    public int getVersionCount() {
        return versions.size();
    }

    @Override
    public Collection<Version> getVersions() {
        return versions.values();
    }

    @Override
    public File getLibrariesDirectory(Version version) {
        return new File(getBaseDirectory(), "libraries");
    }

    @Override
    public File getLibraryFile(Version version, Library lib) {
        if ("local".equals(lib.getHint()) && lib.getFileName() != null)
            return new File(getVersionRoot(version.getId()), "libraries/" + lib.getFileName());
        else
            return new File(getLibrariesDirectory(version), lib.getPath());
    }

    public Path getArtifactFile(Version version, Artifact artifact) {
        return artifact.getPath(getBaseDirectory().toPath().resolve("libraries"));
    }

    public GameDirectoryType getGameDirectoryType(String id) {
        return GameDirectoryType.ROOT_FOLDER;
    }

    @Override
    public File getRunDirectory(String id) {
        switch (getGameDirectoryType(id)) {
            case VERSION_FOLDER: return getVersionRoot(id);
            case ROOT_FOLDER: return getBaseDirectory();
            default: throw new IllegalStateException();
        }
    }

    @Override
    public File getVersionJar(Version version) {
        Version v = version.resolve(this);
        String id = Optional.ofNullable(v.getJar()).orElse(v.getId());
        return new File(getVersionRoot(id), id + ".jar");
    }
    
    @Override
    public File getClientJar(Version version) {
        Version v = version.resolve(this);
        String id = Optional.ofNullable(v.getJar()).orElse(v.getId());
        return new File(getVersionRoot(id), id + ".jar");
    }

    @Override
    public File getServerJar(Version version) {
        Version v = version.resolve(this);
        String id = Optional.ofNullable(v.getJar()).orElse(v.getId());
        return new File(getVersionRoot(id), id + ".server.jar");
    }

    @Override
    public File getNativeDirectory(String id) {
        return new File(getVersionRoot(id), "natives");
    }

    @Override
    public File getVersionRoot(String id) {
        return new File(getBaseDirectory(), "versions/" + id);
    }

    public File getVersionJson(String id) {
        return new File(getVersionRoot(id), id + ".json");
    }

    public Version readVersionJson(String id) throws IOException, JsonParseException {
        return readVersionJson(getVersionJson(id));
    }

    public Version readVersionJson(File file) throws IOException, JsonParseException {
        return JsonUtils.fromNonNullJson(FileUtils.readText(file), Version.class);
    }

    @Override
    public boolean renameVersion(String from, String to) {
        if (EventBus.EVENT_BUS.fireEvent(new RenameVersionEvent(this, from, to)) == Event.Result.DENY)
            return false;

        try {
            Version fromVersion = getVersion(from);
            Path fromDir = getVersionRoot(from).toPath();
            Path toDir = getVersionRoot(to).toPath();
            Files.move(fromDir, toDir);

            Path fromJson = toDir.resolve(from + ".json");
            Path fromClientJar = toDir.resolve(from + ".jar");
            Path fromServerJar = toDir.resolve(from + ".server.jar");
            Path toJson = toDir.resolve(to + ".json");
            Path toClientJar = toDir.resolve(to + ".jar");
            Path toServerJar = toDir.resolve(to + ".server.jar");

            // Either fromClientJar or fromServerJar should exist.
            if (!Files.exists(fromClientJar) && !Files.exists(fromServerJar))
                throw new IOException();

            try {
                Files.move(fromJson, toJson);
                if (Files.exists(fromClientJar))
                    Files.move(fromClientJar, toClientJar);
                if (Files.exists(fromServerJar))
                    Files.move(fromServerJar, toServerJar);
            } catch (IOException e) {
                // recovery
                Lang.ignoringException(() -> Files.move(toJson, fromJson));
                Lang.ignoringException(() -> Files.move(toClientJar, fromClientJar));
                Lang.ignoringException(() -> Files.move(toServerJar, fromServerJar));
                Lang.ignoringException(() -> Files.move(toDir, fromDir));
                throw e;
            }

            if (fromVersion.getId().equals(fromVersion.getJar()))
                fromVersion = fromVersion.setJar(null);
            FileUtils.writeText(toJson.toFile(), JsonUtils.GSON.toJson(fromVersion.setId(to)));

            // fix inheritsFrom of versions that inherits from version [from].
            for (Version version : getVersions()) {
                if (from.equals(version.getInheritsFrom())) {
                    File json = getVersionJson(version.getId()).getAbsoluteFile();
                    FileUtils.writeText(json, JsonUtils.GSON.toJson(version.setInheritsFrom(to)));
                }
            }
            return true;
        } catch (IOException | JsonParseException | VersionNotFoundException | InvalidPathException e) {
            LOG.log(Level.WARNING, "Unable to rename version " + from + " to " + to, e);
            return false;
        }
    }

    public boolean removeVersionFromDisk(String id) {
        if (EventBus.EVENT_BUS.fireEvent(new RemoveVersionEvent(this, id)) == Event.Result.DENY)
            return false;
        if (!versions.containsKey(id))
            return FileUtils.deleteDirectoryQuietly(getVersionRoot(id));
        File file = getVersionRoot(id);
        if (!file.exists())
            return true;
        // test if no file in this version directory is occupied.
        File removedFile = new File(file.getAbsoluteFile().getParentFile(), file.getName() + "_removed");
        if (!file.renameTo(removedFile))
            return false;

        try {
            versions.remove(id);

            if (FileUtils.isMovingToTrashSupported() && FileUtils.moveToTrash(removedFile)) {
                return true;
            }

            // remove json files first to ensure HMCL will not recognize this folder as a valid version.
            List<File> jsons = FileUtils.listFilesByExtension(removedFile, "json");
            jsons.forEach(f -> {
                if (!f.delete())
                    LOG.warning("Unable to delete file " + f);
            });
            // remove the version from version list regardless of whether the directory was removed successfully or not.
            try {
                FileUtils.deleteDirectory(removedFile);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Unable to remove version folder: " + file, e);
            }
            return true;
        } finally {
            refreshVersionsAsync().start();
        }
    }

    protected void refreshVersionsImpl() {
        Map<String, Version> versions = new TreeMap<>();

        if (ClassicVersion.hasClassicVersion(getBaseDirectory())) {
            Version version = new ClassicVersion();
            versions.put(version.getId(), version);
        }

        SimpleVersionProvider provider = new SimpleVersionProvider();

        File[] files = new File(getBaseDirectory(), "versions").listFiles();
        if (files != null)
            Arrays.stream(files).parallel().filter(File::isDirectory).flatMap(dir -> {
                String id = dir.getName();
                File json = new File(dir, id + ".json");

                // If user renamed the json file by mistake or created the json file in a wrong name,
                // we will find the only json and rename it to correct name.
                if (!json.exists()) {
                    List<File> jsons = FileUtils.listFilesByExtension(dir, "json");
                    if (jsons.size() == 1) {
                        LOG.info("Renaming json file " + jsons.get(0) + " to " + json);
                        if (!jsons.get(0).renameTo(json)) {
                            LOG.warning("Cannot rename json file, ignoring version " + id);
                            return Stream.empty();
                        }

                        File jar = new File(dir, FileUtils.getNameWithoutExtension(jsons.get(0)) + ".jar");
                        File renameJar = null;
                        if (jar.getName().endsWith(".server.jar")) renameJar = new File(dir, id + ".server.jar");
                        else renameJar = new File(dir, id + ".jar");
                        if (jar.exists() && !jar.renameTo(renameJar)) {
                            LOG.warning("Cannot rename jar file, ignoring version " + id);
                            return Stream.empty();
                        }
                    } else {
                        LOG.info("No available json file found, ignoring version " + id);
                        return Stream.empty();
                    }
                }

                Version version;
                try {
                    version = readVersionJson(json);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Malformed version json " + id, e);
                    // JsonSyntaxException or IOException or NullPointerException(!!)
                    if (EventBus.EVENT_BUS.fireEvent(new GameJsonParseFailedEvent(this, json, id)) != Event.Result.ALLOW)
                        return Stream.empty();

                    try {
                        version = readVersionJson(json);
                    } catch (Exception e2) {
                        LOG.log(Level.SEVERE, "User corrected version json is still malformed", e2);
                        return Stream.empty();
                    }
                }

                if (!id.equals(version.getId())) {
                    version = version.setId(id);
                    try {
                        FileUtils.writeText(json, JsonUtils.GSON.toJson(version));
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Ignoring version " + id + " because wrong id " + version.getId() + " is set and cannot correct it.", e);
                        return Stream.empty();
                    }
                }

                return Stream.of(version);
            }).forEachOrdered(provider::addVersion);

        for (Version version : provider.getVersionMap().values()) {
            try {
                Version resolved = version.resolve(provider);

                if (resolved.appliesToCurrentEnvironment() &&
                        EventBus.EVENT_BUS.fireEvent(new LoadedOneVersionEvent(this, resolved)) != Event.Result.DENY)
                    versions.put(version.getId(), version);
            } catch (VersionNotFoundException e) {
                LOG.log(Level.WARNING, "Ignoring version " + version.getId() + " because it inherits from a nonexistent version.");
            }
        }

        this.versions = versions;
    }

    @Override
    public void refreshVersions() {
        if (EventBus.EVENT_BUS.fireEvent(new RefreshingVersionsEvent(this)) == Event.Result.DENY)
            return;

        refreshVersionsImpl();
        EventBus.EVENT_BUS.fireEvent(new RefreshedVersionsEvent(this));
    }

    @Override
    public AssetIndex getAssetIndex(String version, String assetId) throws IOException {
        try {
            return Objects.requireNonNull(JsonUtils.GSON.fromJson(FileUtils.readText(getIndexFile(version, assetId)), AssetIndex.class));
        } catch (JsonParseException | NullPointerException e) {
            throw new IOException("Asset index file malformed", e);
        }
    }

    @Override
    public File getActualAssetDirectory(String version, String assetId) {
        try {
            return reconstructAssets(version, assetId);
        } catch (IOException | JsonParseException e) {
            LOG.log(Level.SEVERE, "Unable to reconstruct asset directory", e);
            return getAssetDirectory(version, assetId);
        }
    }

    @Override
    public File getAssetDirectory(String version, String assetId) {
        return new File(getBaseDirectory(), "assets");
    }

    @Override
    public File getAssetObject(String version, String assetId, String name) throws IOException {
        try {
            return getAssetObject(version, assetId, getAssetIndex(version, assetId).getObjects().get(name));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Unrecognized asset object " + name + " in asset " + assetId + " of version " + version, e);
        }
    }

    @Override
    public File getAssetObject(String version, String assetId, AssetObject obj) {
        return getAssetObject(version, getAssetDirectory(version, assetId), obj);
    }

    public File getAssetObject(String version, File assetDir, AssetObject obj) {
        return new File(assetDir, "objects/" + obj.getLocation());
    }

    @Override
    public File getIndexFile(String version, String assetId) {
        return new File(getAssetDirectory(version, assetId), "indexes/" + assetId + ".json");
    }

    @Override
    public File getLoggingObject(String version, String assetId, LoggingInfo loggingInfo) {
        return new File(getAssetDirectory(version, assetId), "log_configs/" + loggingInfo.getFile().getId());
    }

    protected File reconstructAssets(String version, String assetId) throws IOException, JsonParseException {
        File assetsDir = getAssetDirectory(version, assetId);
        File indexFile = getIndexFile(version, assetId);
        File virtualRoot = new File(new File(assetsDir, "virtual"), assetId);

        if (!indexFile.isFile())
            return assetsDir;

        String assetIndexContent = FileUtils.readText(indexFile);
        AssetIndex index = JsonUtils.GSON.fromJson(assetIndexContent, AssetIndex.class);

        if (index == null)
            return assetsDir;

        if (index.isVirtual()) {
            int cnt = 0;
            int tot = index.getObjects().entrySet().size();
            for (Map.Entry<String, AssetObject> entry : index.getObjects().entrySet()) {
                File target = new File(virtualRoot, entry.getKey());
                File original = getAssetObject(version, assetsDir, entry.getValue());
                if (original.exists()) {
                    cnt++;
                    if (!target.isFile())
                        FileUtils.copyFile(original, target);
                }
            }

            // If the scale new format existent file is lower then 0.1, use the old format.
            if (cnt * 10 < tot)
                return assetsDir;
            else
                return virtualRoot;
        }

        return assetsDir;
    }

    public Task<Version> saveAsync(Version version) {
        if (version.isResolvedPreservingPatches()) {
            return new VersionJsonSaveTask(this, MaintainTask.maintainPreservingPatches(this, version));
        } else {
            return new VersionJsonSaveTask(this, version);
        }
    }

    public boolean isLoaded() {
        return versions != null;
    }

    public File getModpackConfiguration(String version) {
        return new File(getVersionRoot(version), "modpack.json");
    }

    /**
     * read modpack configuration for a version.
     * @param version version installed as modpack
     * @param <M> manifest type of ModpackConfiguration
     * @return modpack configuration object, or null if this version is not a modpack.
     * @throws VersionNotFoundException if version does not exist.
     * @throws IOException if an i/o error occurs.
     */
    @Nullable
    public <M> ModpackConfiguration<M> readModpackConfiguration(String version) throws IOException, VersionNotFoundException {
        if (!hasVersion(version)) throw new VersionNotFoundException(version);
        File file = getModpackConfiguration(version);
        if (!file.exists()) return null;
        return JsonUtils.GSON.fromJson(FileUtils.readText(file), new TypeToken<ModpackConfiguration<M>>(){}.getType());
    }

    public boolean isModpack(String version) {
        return getModpackConfiguration(version).exists();
    }

    public ModManager getModManager(String version) {
        return new ModManager(this, version);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("versions", versions == null ? null : versions.keySet())
                .append("baseDirectory", baseDirectory)
                .toString();
    }

    @Override
    public File getMinecraftRoot() {
        return getBaseDirectory();
    }
}
