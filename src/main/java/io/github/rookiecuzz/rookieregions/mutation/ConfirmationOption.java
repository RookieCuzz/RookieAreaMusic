package io.github.rookiecuzz.rookieregions.mutation;

/** Player-facing opaque token for one server-selected placement option. */
public record ConfirmationOption(String token,
                                 PlacementOption option,
                                 long expiresAtMillis) {
    public ConfirmationOption {
        if(token == null || token.trim().isEmpty()){
            throw new IllegalArgumentException("confirmation token must not be blank");
        }
        token = token.trim();
        if(option == null){
            throw new IllegalArgumentException("confirmation option cannot be null");
        }
        if(expiresAtMillis < 0L){
            throw new IllegalArgumentException("confirmation expiry cannot be negative");
        }
    }
}
