package io.github.rookiecuzz.rookieregions.rule;

/** Declares which kind of query subject a flag can meaningfully target. */
public enum ActorScope {
    ANY,
    PLAYER,
    NON_PLAYER;

    public boolean accepts(Subject subject) {
        boolean player = subject != null && subject.playerId() != null;
        return switch(this) {
            case ANY -> true;
            case PLAYER -> player;
            case NON_PLAYER -> !player;
        };
    }
}
