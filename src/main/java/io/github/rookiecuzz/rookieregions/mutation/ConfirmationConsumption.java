package io.github.rookiecuzz.rookieregions.mutation;

import java.util.Optional;

public record ConfirmationConsumption(ConfirmationConsumeStatus status,
                                      Optional<ConfirmationAuthorization> authorization) {
    public ConfirmationConsumption {
        if(status == null){
            throw new IllegalArgumentException("confirmation status cannot be null");
        }
        authorization = authorization == null ? Optional.empty() : authorization;
        if((status == ConfirmationConsumeStatus.AUTHORIZED)
                != authorization.isPresent()){
            throw new IllegalArgumentException(
                    "only an authorized consumption may carry authorization"
            );
        }
    }
}
