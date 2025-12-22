package dev.lyphium.filesync;

import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.kyori.adventure.text.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class SynchronisedObjectArgument implements CustomArgumentType.Converted<SynchronisedObject, String> {

    private final Collection<SynchronisedObject> objects;
    private final List<SyncMode> modes;

    public SynchronisedObjectArgument(Collection<SynchronisedObject> objects, SyncMode mode) {
        this.objects = objects;
        this.modes = List.of(mode);
    }

    public SynchronisedObjectArgument(Collection<SynchronisedObject> objects, List<SyncMode> modes) {
        this.objects = objects;
        this.modes = modes;
    }

    @Override
    public SynchronisedObject convert(String nativeType) throws CommandSyntaxException {
        for (final SynchronisedObject obj : objects) {
            if (modes.contains(obj.mode())) {
                if (obj.name().equals(nativeType)) {
                    return obj;
                }
            }
        }

        final Message message = MessageComponentSerializer.message().serialize(Component.text(nativeType + " is not a valid object!"));
        throw new CommandSyntaxException(new SimpleCommandExceptionType(message), message);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        for (final SynchronisedObject obj : objects) {
            if (modes.contains(obj.mode())) {
                if (obj.name().toLowerCase(Locale.ROOT).contains(builder.getRemainingLowerCase())) {
                    builder.suggest(StringArgumentType.escapeIfRequired(obj.name()));
                }
            }
        }

        return builder.buildFuture();
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.string();
    }

}
