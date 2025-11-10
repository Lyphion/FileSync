package dev.lyphium.filesync;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class FileSyncCommand {

    public static final String DESCRIPTION = "Central command for file sync";
    public static final String PERMISSION = "filesync.admin";

    private final FileSyncManager fileSyncManager;

    public FileSyncCommand(FileSyncManager fileSyncManager) {
        this.fileSyncManager = fileSyncManager;
    }

    public LiteralCommandNode<CommandSourceStack> construct() {
        return Commands.literal("filesync").requires(s -> s.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("reload").executes(ctx -> {
                    final CommandSender sender = ctx.getSource().getSender();
                    fileSyncManager.loadConfig();
                    sender.sendActionBar(Component.text("FileSync Reloaded", NamedTextColor.GREEN));

                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("publish").requires(s -> !fileSyncManager.isReadonly())
                        .then(Commands.argument("object", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> {
                                    fileSyncManager.getObjects().keySet()
                                            .stream()
                                            .filter(name -> name.toLowerCase(Locale.ROOT).contains(builder.getRemainingLowerCase()))
                                            .forEach(builder::suggest);

                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    final CommandSender sender = ctx.getSource().getSender();
                                    final String obj = StringArgumentType.getString(ctx, "object");

                                    for (final SynchronisedObject object : fileSyncManager.getObjects().values()) {
                                        if (!object.name().equalsIgnoreCase(obj))
                                            continue;

                                        final String version = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(LocalDateTime.now());
                                        final boolean success = fileSyncManager.publish(object, version);

                                        if (success) {
                                            sender.sendActionBar(Component.text("New version published", NamedTextColor.GREEN));
                                        } else {
                                            sender.sendActionBar(Component.text("Error publishing version", NamedTextColor.RED));
                                        }

                                        return Command.SINGLE_SUCCESS;
                                    }

                                    sender.sendActionBar(Component.text("Unknown object", NamedTextColor.RED));
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .build();
    }
}
