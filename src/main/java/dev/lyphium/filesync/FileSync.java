package dev.lyphium.filesync;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.UnknownNullability;
import org.jspecify.annotations.Nullable;

public final class FileSync extends JavaPlugin {

    @Getter
    private @UnknownNullability static FileSync instance;

    private @Nullable FileSyncManager fileSyncManager;

    public FileSync() {
        instance = this;
    }

    @Override
    public void onEnable() {
        fileSyncManager = new FileSyncManager(this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            final Commands registrar = commands.registrar();

            final FileSyncCommand fileSyncCommand = new FileSyncCommand(fileSyncManager);
            registrar.register(fileSyncCommand.construct(), FileSyncCommand.DESCRIPTION);
        });
    }

    @Override
    public void onDisable() {
        if (fileSyncManager != null)
            fileSyncManager.disable();
    }
}
