package dev.lyphium.filesync;

import lombok.Getter;
import org.apache.commons.io.file.PathUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class FileSyncManager {

    private static final String METADATA = ".info";
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final JavaPlugin plugin;
    private final Path serverPath = Bukkit.getWorldContainer().toPath();
    private Path sharedLocation;

    @Getter
    private final Map<String, SynchronisedObject> objects = new HashMap<>();
    private final Map<String, String> nameLookup = new HashMap<>();

    private final List<WatchKey> watchKeys = new ArrayList<>();
    private @Nullable WatchService watcher;
    private @Nullable BukkitTask syncTask;

    public FileSyncManager(JavaPlugin plugin) {
        this.plugin = plugin;

        loadConfig();
    }

    /**
     * Load config values.
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        watchKeys.forEach(WatchKey::cancel);
        watchKeys.clear();

        final FileConfiguration config = plugin.getConfig();

        final boolean startUpSync = config.getBoolean("StartUpSync");
        sharedLocation = serverPath.resolve(Objects.requireNonNull(config.getString("SharedLocation")));

        objects.clear();
        nameLookup.clear();

        // Create new watcher if none exist
        if (watcher == null) {
            try {
                watcher = FileSystems.getDefault().newWatchService();
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to open watch service", ex);
                return;
            }

            syncTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, this::watchFiles);
        }

        // Load shared object settings
        final ConfigurationSection section = config.getConfigurationSection("Synchronizations");
        for (final String name : Objects.requireNonNull(section).getKeys(false)) {
            final Path[] locations = config.getStringList("Synchronizations." + name + ".Locations")
                    .stream()
                    .map(Paths::get)
                    .toArray(Path[]::new);
            final String command = config.getString("Synchronizations." + name + ".Command");
            final String modeValue = config.getString("Synchronizations." + name + ".Mode", "none");
            final SyncMode mode = Objects.requireNonNullElse(SyncMode.fromName(modeValue), SyncMode.NONE);
            final int versions = config.getInt("Synchronizations." + name + ".Versions", -1);

            final SynchronisedObject object = new SynchronisedObject(name, locations, command, mode, versions);
            objects.put(name, object);

            final Path info = sharedLocation.resolve(name).resolve(METADATA);
            nameLookup.put(info.toAbsolutePath().toString(), name);

            // If no version is available create one
            switch (mode) {
                case PUSH -> {
                    if (startUpSync && !Files.exists(info)) {
                        final String version = FORMATTER.format(LocalDateTime.now());
                        publish(object, version);
                    }
                }
                case PULL -> {
                    // Create a new watcher key for the main object folder
                    try {
                        assert watcher != null;
                        final WatchKey key = info.getParent().register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
                        watchKeys.add(key);
                    } catch (IOException ex) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to register watch key for " + name, ex);
                        continue;
                    }

                    if (!startUpSync)
                        continue;

                    // Synchronize files from shared folder
                    final String version;
                    try {
                        version = Files.readString(info);
                    } catch (IOException ex) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to read info for " + name, ex);
                        continue;
                    }

                    sync(object, version);
                }
            }
        }
    }

    /**
     * Publish a new version of a shared object.
     *
     * @param object  Object to publish
     * @param version Version of the object
     * @return {@code true} if publish was successful.
     */
    public boolean publish(SynchronisedObject object, String version) {
        final Path folder = sharedLocation.resolve(object.name());
        final Path file = folder.resolve(version + ".zip");

        // Create parent folder
        try {
            if (Files.notExists(folder))
                Files.createDirectories(folder);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create folder for " + object.name(), ex);
        }

        try (final OutputStream fos = Files.newOutputStream(file);
             final ZipOutputStream stream = new ZipOutputStream(fos)) {
            for (final Path location : object.locations()) {
                final Path base = serverPath.resolve(location);

                // Add file to zip
                if (!Files.isDirectory(base)) {
                    final ZipEntry entry = new ZipEntry(serverPath.relativize(base).toString());
                    entry.setLastModifiedTime(Files.getLastModifiedTime(base));
                    stream.putNextEntry(entry);
                    Files.copy(base, stream);
                    stream.closeEntry();
                    continue;
                }

                // Add folder to zip
                Files.walkFileTree(base, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        final ZipEntry entry = new ZipEntry(serverPath.relativize(file).toString());
                        entry.setLastModifiedTime(Files.getLastModifiedTime(file));
                        stream.putNextEntry(entry);
                        Files.copy(file, stream);
                        stream.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create zip file for " + object.name(), ex);
            return false;
        }

        // Update info file to point to new version
        try {
            final Path info = folder.resolve(METADATA);
            Files.writeString(info, version, Files.exists(info) ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.CREATE);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write info file for " + object.name(), ex);
            return false;
        }

        plugin.getLogger().log(Level.INFO, "Synchronized object " + object.name() + " (version " + version + ")");

        // Remove old versions
        if (object.versions() > 0) {
            try (final Stream<Path> stream = Files.list(folder)) {
                final List<LocalDateTime> paths = stream
                        .filter(p -> p.getFileName().toString().endsWith(".zip"))
                        .map(p -> p.getFileName().toString())
                        .map(p -> p.substring(0, p.length() - ".zip".length()))
                        .map(p -> {
                            try {
                                return LocalDateTime.parse(p, FORMATTER);
                            } catch (Exception ignored) {
                                // Skip other formats
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .sorted(Comparator.reverseOrder())
                        .collect(Collectors.toCollection(ArrayList::new));

                // Remove old versions
                while (paths.size() > object.versions()) {
                    final LocalDateTime dt = paths.removeLast();
                    final Path path = folder.resolve(FORMATTER.format(dt) + ".zip");
                    plugin.getLogger().log(Level.INFO, "Removed old version of object " + object.name() + " (version " + FORMATTER.format(dt) + ")");
                    Files.delete(path);
                }
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to remove old versions for " + object.name(), ex);
            }
        }

        return true;
    }

    /**
     * Synchronize a new version of a shared object.
     *
     * @param object Object to synchronize
     */
    public boolean sync(SynchronisedObject object) {
        final Path info = sharedLocation.resolve(object.name()).resolve(METADATA);

        final String version;
        try {
            version = Files.readString(info);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to read info file for " + object.name());
            return false;
        }

        try {
            return sync(object, version);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to synchronize object " + object.name(), ex);
            return false;
        }
    }

    /**
     * Synchronize a new version of a shared object.
     *
     * @param object  Object to synchronize
     * @param version Version of the object
     */
    private boolean sync(SynchronisedObject object, String version) {
        final Path folder = sharedLocation.resolve(object.name());
        final Path file = folder.resolve(version + ".zip");

        if (!Files.exists(file))
            return false;

        for (final Path location : object.locations()) {
            final Path path = serverPath.resolve(location);

            if (Files.exists(path)) {
                try {
                    PathUtils.delete(path);
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to delete old files for object " + object.name() + " at " + path, ex);
                }
            }
        }

        try (final InputStream fis = Files.newInputStream(file);
             final ZipInputStream stream = new ZipInputStream(fis)) {

            ZipEntry entry = stream.getNextEntry();
            while (entry != null) {
                final Path relativePath = Path.of(entry.getName());
                if (Arrays.stream(object.locations()).anyMatch(relativePath::startsWith)) {
                    final Path path = serverPath.resolve(relativePath);

                    if (!Files.exists(path.getParent()))
                        Files.createDirectories(path.getParent());

                    Files.copy(stream, path, StandardCopyOption.REPLACE_EXISTING);
                    Files.setLastModifiedTime(path, entry.getLastModifiedTime());
                }

                entry = stream.getNextEntry();
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to copy files for " + object.name(), ex);
            return false;
        }

        if (object.command() != null)
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), object.command()));
        plugin.getLogger().log(Level.INFO, "Synchronized object " + object.name() + " (version " + version + ")");
        return true;
    }

    /**
     * Watch for file changes in a directory.
     */
    private void watchFiles() {
        while (true) {
            final WatchKey key;
            try {
                assert watcher != null;
                key = watcher.take();
            } catch (ClosedWatchServiceException | InterruptedException ex) {
                break;
            }

            // Read events
            for (final WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW)
                    continue;

                @SuppressWarnings("unchecked") WatchEvent<Path> ev = (WatchEvent<Path>) event;
                final Path root = (Path) key.watchable();
                final Path file = root.resolve(ev.context());

                // Check if info file was updated, otherwise ignore event
                if (!ev.context().endsWith(METADATA))
                    continue;

                // Read version from info file
                final String name = nameLookup.get(file.toAbsolutePath().toString());
                final SynchronisedObject object = objects.get(name);

                final String version;
                try {
                    version = Files.readString(file);
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.WARNING, "Failed to read file: " + file, ex);
                    continue;
                }

                // Update files
                sync(object, version);
            }

            boolean valid = key.reset();
            if (!valid) break;
        }
    }

    /**
     * Disable watching.
     */
    public void disable() {
        if (syncTask != null)
            syncTask.cancel();

        try {
            watchKeys.forEach(WatchKey::cancel);
            if (watcher != null)
                watcher.close();
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to close watcher", ex);
        }
    }
}
