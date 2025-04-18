package dev.lyphium.filesync;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Object describing an instance of a shared and synchronized object
 *
 * @param name      Name of the object
 * @param locations Collection of paths to synchronize
 * @param command   Command to execute, when synchronization succeeds
 */
public record SynchronisedObject(@NotNull String name, @NotNull Path @NotNull [] locations, @NotNull String command) {
}
