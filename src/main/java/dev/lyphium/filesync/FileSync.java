package dev.lyphium.filesync;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class FileSync extends JavaPlugin {

    private FileSyncManager fileSyncManager;

    @Override
    public void onEnable() {
        fileSyncManager = new FileSyncManager(this);

        new FileSyncCommand(fileSyncManager).register(Objects.requireNonNull(getCommand("filesync")));

        getLogger().info("Plugin activated");
    }

    @Override
    public void onDisable() {
        fileSyncManager.disable();

        getLogger().info("Plugin deactivated");
    }
}
