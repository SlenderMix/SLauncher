package ru.spark.slauncher.auth;

/**
 * Thrown when a previously existing character cannot be found.
 */
public final class CharacterDeletedException extends AuthenticationException {
    public CharacterDeletedException() {
    }
}
