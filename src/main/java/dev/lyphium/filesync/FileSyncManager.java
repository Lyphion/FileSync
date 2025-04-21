package dev.lyphium.filesync;

import lombok.Getter;
import org.apache.commons.io.file.PathUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

public final class FileSyncManager {

    private final JavaPlugin plugin;
    private final Path serverPath = Bukkit.getWorldContainer().toPath();

    private static final String metadata = ".info";

    @Getter
    private boolean readonly;
    private Path sharedLocation;

    @Getter
    private final Map<String, SynchronisedObject> objects = new HashMap<>();
    private final Map<String, String> nameLookup = new HashMap<>();

    private final List<WatchKey> watchKeys = new ArrayList<>();
    private WatchService watcher;
    private BukkitTask syncTask;

    public FileSyncManager(@NotNull JavaPlugin plugin) {
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
        readonly = config.getBoolean("ReadOnly");
        sharedLocation = serverPath.resolve(Objects.requireNonNull(config.getString("SharedLocation")));

        objects.clear();
        nameLookup.clear();

        // Create new watcher if none exist
        if (readonly && watcher == null) {
            try {
                this.watcher = FileSystems.getDefault().newWatchService();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to open watch service", e);
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

            final SynchronisedObject object = new SynchronisedObject(name, locations, Objects.requireNonNull(command));
            objects.put(name, object);

            final Path info = sharedLocation.resolve(name).resolve(metadata);
            nameLookup.put(info.toAbsolutePath().toString(), name);

            // If no version is available create one
            if (!readonly) {
                if (startUpSync && !Files.exists(info)) {
                    final String version = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(LocalDateTime.now());
                    publish(object, version);
                }
                continue;
            }

            // Create a new watcher key for the main object folder
            try {
                assert watcher != null;
                final WatchKey key = info.getParent().register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
                watchKeys.add(key);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to register watch key for " + name, e);
                continue;
            }

            if (!startUpSync)
                continue;

            // Synchronize files from shared folder
            final String version;
            try {
                version = Files.readString(info);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to read info for " + name, e);
                continue;
            }

            sync(object, version);
        }
    }

    /**
     * Publish a new version of a shared object.
     *
     * @param object  Object to publish
     * @param version Version of the object
     * @return {@code true} if publish was successful.
     */
    public boolean publish(@NotNull SynchronisedObject object, @NotNull String version) {
        final Path root = sharedLocation.resolve(object.name() + "/" + version);

        // Copy all files to shared folder
        boolean success = true;
        for (final Path location : object.locations()) {
            success &= copy(serverPath.resolve(location), root.resolve(location));
        }

        if (!success)
            return false;

        // Update info file to point to new version
        try {
            final Path info = sharedLocation.resolve(object.name()).resolve(metadata);
            Files.writeString(info, version, Files.exists(info) ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.CREATE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write info file", e);
            return false;
        }

        plugin.getLogger().log(Level.INFO, "Synchronized object " + object.name() + " (version " + version + ")");
        return true;
    }

    /**
     * Synchronize a new version of a shared object.
     *
     * @param object  Object to synchronize
     * @param version Version of the object
     */
    private void sync(@NotNull SynchronisedObject object, @NotNull String version) {
        final Path root = sharedLocation.resolve(object.name() + "/" + version);

        // Copy all files to server folders
        for (final Path location : object.locations()) {
            final Path src = root.resolve(location);

            if (!Files.exists(src))
                continue;

            copy(src, serverPath.resolve(location));
        }

        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), object.command()));
        plugin.getLogger().log(Level.INFO, "Synchronized object " + object.name() + " (version " + version + ")");
    }

    /**
     * Copy a file or folder from source to target, and replace existing ones.
     *
     * @param src    Copy source
     * @param target Copy target
     * @return {@code true} if copy was successful.
     */
    private boolean copy(@NotNull Path src, @NotNull Path target) {
        try {
            // Create parent folder
            if (Files.notExists(target.getParent()))
                Files.createDirectories(target.getParent());

            // Copy file
            if (!Files.isDirectory(src)) {
                Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return true;
            }

            // Copy directory
            if (Files.exists(target))
                PathUtils.deleteDirectory(target);
            PathUtils.copyDirectory(src, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to copy file " + src, e);
            return false;
        }
    }

    /**
     * Watch for file changes in a directory.
     */
    private void watchFiles() {
        while (true) {
            final WatchKey key;
            try {
                key = watcher.take();
            } catch (ClosedWatchServiceException | InterruptedException e) {
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
                if (!ev.context().endsWith(metadata))
                    continue;

                // Read version from info file
                final String name = nameLookup.get(file.toAbsolutePath().toString());
                final SynchronisedObject object = objects.get(name);

                final String version;
                try {
                    version = Files.readString(file);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to read file: " + file);
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
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to close watcher: " + e.getMessage());
        }
    }
}
