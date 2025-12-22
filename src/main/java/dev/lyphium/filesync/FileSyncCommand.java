package dev.lyphium.filesync;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.time.LocalDateTime;
import java.util.List;

public final class FileSyncCommand {

    public static final String DESCRIPTION = "Central command for file sync";
    public static final String PERMISSION = "filesync.admin";

    private final FileSyncManager manager;

    public FileSyncCommand(FileSyncManager manager) {
        this.manager = manager;
    }

    public LiteralCommandNode<CommandSourceStack> construct() {
        return Commands.literal("filesync").requires(s -> s.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("reload").executes(ctx -> {
                    final CommandSender sender = ctx.getSource().getSender();

                    Bukkit.getScheduler().runTaskAsynchronously(FileSync.getInstance(), () -> {
                        manager.loadConfig();
                        sender.sendActionBar(Component.text("FileSync Reloaded", NamedTextColor.GREEN));
                    });

                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("publish")
                        .then(Commands.argument("object", new SynchronisedObjectArgument(manager.getObjects().values(), SyncMode.PUSH))
                                .executes(ctx -> {
                                    final CommandSender sender = ctx.getSource().getSender();
                                    final SynchronisedObject object = ctx.getArgument("object", SynchronisedObject.class);

                                    final String version = FileSyncManager.FORMATTER.format(LocalDateTime.now());

                                    Bukkit.getScheduler().runTaskAsynchronously(FileSync.getInstance(), () -> {
                                        final boolean success = manager.publish(object, version);

                                        if (success) {
                                            sender.sendActionBar(Component.text("New version published", NamedTextColor.GREEN));
                                        } else {
                                            sender.sendActionBar(Component.text("Error publishing version", NamedTextColor.RED));
                                        }
                                    });


                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(Commands.literal("sync")
                        .then(Commands.argument("object", new SynchronisedObjectArgument(manager.getObjects().values(), List.of(SyncMode.PULL, SyncMode.MANUAL)))
                                .executes(ctx -> {
                                    final CommandSender sender = ctx.getSource().getSender();
                                    final SynchronisedObject object = ctx.getArgument("object", SynchronisedObject.class);

                                    Bukkit.getScheduler().runTaskAsynchronously(FileSync.getInstance(), () -> {
                                        final boolean success = manager.sync(object);

                                        if (success) {
                                            sender.sendActionBar(Component.text("Synchronized version", NamedTextColor.GREEN));
                                        } else {
                                            sender.sendActionBar(Component.text("Error synchronizing version", NamedTextColor.RED));
                                        }
                                    });

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .build();
    }
}
