package dev.lyphium.filesync;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public final class FileSyncCommand implements CommandExecutor, TabCompleter {

    private final FileSyncManager fileSyncManager;

    public FileSyncCommand(@NotNull FileSyncManager fileSyncManager) {
        this.fileSyncManager = fileSyncManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args[0].equalsIgnoreCase("reload")) {
            if (args.length != 1)
                return false;

            fileSyncManager.loadConfig();
            sender.sendActionBar(Component.text("FileSync Reloaded", TextColor.color(0x1EFF41)));
            return true;
        } else if (args[0].equalsIgnoreCase("publish")) {
            if (fileSyncManager.isReadonly()) {
                sender.sendActionBar(Component.text("Server is in readonly mode", TextColor.color(0xFF3500)));
                return true;
            }

            if (args.length != 2)
                return false;

            for (final SynchronisedObject object : fileSyncManager.getObjects().values()) {
                if (object.name().equalsIgnoreCase(args[1])) {
                    final String version = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(LocalDateTime.now());
                    final boolean success = fileSyncManager.publish(object, version);

                    if (success) {
                        sender.sendActionBar(Component.text("New version published", TextColor.color(0x1EFF41)));
                    } else {
                        sender.sendActionBar(Component.text("Error publishing version", TextColor.color(0xFF3500)));
                    }
                    return true;
                }
            }

            sender.sendActionBar(Component.text("Unknown object", TextColor.color(0xFF3500)));
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        final String name = args[0].toLowerCase();

        if (args.length == 1) {
            final String[] completions = fileSyncManager.isReadonly() ? new String[]{"reload"} : new String[]{"reload", "publish"};
            return Arrays.stream(completions)
                    .filter(s -> s.startsWith(name))
                    .toList();
        }

        if (args.length == 2 && name.equals("publish") && !fileSyncManager.isReadonly()) {
            return fileSyncManager.getObjects().keySet()
                    .stream()
                    .filter(s -> s.startsWith(name))
                    .toList();
        }

        return List.of();
    }

    /**
     * Set this object as an executor and tab completer for the command.
     *
     * @param command Command to be handled.
     */
    public void register(@NotNull PluginCommand command) {
        command.setExecutor(this);
        command.setTabCompleter(this);
    }
}
