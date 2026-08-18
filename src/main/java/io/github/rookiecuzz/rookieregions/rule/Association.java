package io.github.rookiecuzz.rookieregions.rule;

/** A subject's ownership association with one applicable leaf. */
public enum Association {
    OWNER,
    MEMBER,
    NON_MEMBER;

    public boolean isAssociated() {
        return this == OWNER || this == MEMBER;
    }
}
