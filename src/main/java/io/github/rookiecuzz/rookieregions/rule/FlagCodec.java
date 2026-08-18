package io.github.rookiecuzz.rookieregions.rule;

/** Persistence-neutral scalar/tree codec used later by a storage adapter. */
public interface FlagCodec<T> {
    Object encode(T value);

    T decode(Object encoded);
}
