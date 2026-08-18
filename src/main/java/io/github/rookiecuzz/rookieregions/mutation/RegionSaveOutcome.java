package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;

import java.util.List;

/** Exactly five terminal outcomes for one save attempt. */
public sealed interface RegionSaveOutcome permits RegionSaveOutcome.Saved,
        RegionSaveOutcome.ConfirmationRequired,
        RegionSaveOutcome.Rejected,
        RegionSaveOutcome.Stale,
        RegionSaveOutcome.Failed {

    default RegionSaveStatus status() {
        return switch(this) {
            case Saved ignored -> RegionSaveStatus.SAVED;
            case ConfirmationRequired ignored ->
                    RegionSaveStatus.CONFIRMATION_REQUIRED;
            case Rejected ignored -> RegionSaveStatus.DENIED;
            case Stale ignored -> RegionSaveStatus.STALE;
            case Failed ignored -> RegionSaveStatus.STORAGE_FAILURE;
        };
    }

    record Saved(RegionSnapshot snapshot,
                 Region region,
                 SaveChoice choice) implements RegionSaveOutcome {
        public Saved {
            if(snapshot == null || region == null || choice == null){
                throw new IllegalArgumentException(
                        "saved outcome fields must not be null"
                );
            }
        }
    }

    record ConfirmationRequired(String placementPlanFingerprint,
                                List<ConfirmationOption> options)
            implements RegionSaveOutcome {
        public ConfirmationRequired {
            if(placementPlanFingerprint == null
                    || placementPlanFingerprint.trim().isEmpty()){
                throw new IllegalArgumentException(
                        "placement plan fingerprint must not be blank"
                );
            }
            if(options == null || options.isEmpty()){
                throw new IllegalArgumentException(
                        "confirmation must contain at least one option"
                );
            }
            options = List.copyOf(options);
        }
    }

    record Rejected(RegionSaveRejection reason,
                    String message) implements RegionSaveOutcome {
        public Rejected {
            if(reason == null){
                throw new IllegalArgumentException("rejection reason cannot be null");
            }
            message = message == null ? "" : message;
        }
    }

    record Stale(StaleReason reason,
                 String message) implements RegionSaveOutcome {
        public Stale {
            if(reason == null){
                throw new IllegalArgumentException("stale reason cannot be null");
            }
            message = message == null ? "" : message;
        }
    }

    record Failed(String message,
                  Exception cause) implements RegionSaveOutcome {
        public Failed {
            message = message == null ? "" : message;
            if(cause == null){
                throw new IllegalArgumentException("failure cause cannot be null");
            }
        }
    }
}
