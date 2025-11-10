package dev.lyphium.filesync;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

public final class FileSync extends JavaPlugin {

    private @Nullable FileSyncManager fileSyncManager;

    @Override
    public void onEnable() {
        fileSyncManager = new FileSyncManager(this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            final Commands registrar = commands.registrar();

            final FileSyncCommand fileSyncCommand = new FileSyncCommand(fileSyncManager);
            registrar.register(fileSyncCommand.construct(), FileSyncCommand.DESCRIPTION);
        });

        getLogger().info("Plugin activated");
    }

    @Override
    public void onDisable() {
        if (fileSyncManager != null)
            fileSyncManager.disable();

        getLogger().info("Plugin deactivated");
    }
}
