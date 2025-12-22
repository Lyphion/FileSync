package dev.lyphium.filesync;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Object describing an instance of a shared and synchronized object.
 *
 * @param name      Name of the object
 * @param locations Collection of paths to synchronize
 * @param command   Command to execute, when synchronization succeeds
 * @param mode      Mode of the object
 * @param versions  Number of versions to keep if in {@code Mode.PUSH} (none positive values -> keep all)
 */
public record SynchronisedObject(String name, Path[] locations, @Nullable String command, SyncMode mode, int versions) {
}
