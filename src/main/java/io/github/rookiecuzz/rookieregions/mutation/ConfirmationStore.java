package io.github.rookiecuzz.rookieregions.mutation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * In-memory, one-use confirmation challenges. Tokens expire after exactly
 * thirty seconds and are bound to actor, session, candidate, plan, revision,
 * and one concrete save choice.
 */
public final class ConfirmationStore {
    public static final long TTL_MILLIS = 30_000L;

    private final LongSupplier nowMillis;
    private final Supplier<String> tokenSupplier;
    private long ttlMillis;
    private final Map<String, StoredConfirmation> confirmations =
            new LinkedHashMap<>();

    public ConfirmationStore(){
        this(Duration.ofMillis(TTL_MILLIS));
    }

    public ConfirmationStore(Duration lifetime){
        this(lifetime, System::currentTimeMillis, () -> UUID.randomUUID().toString());
    }

    public ConfirmationStore(LongSupplier nowMillis,
                             Supplier<String> tokenSupplier) {
        this(Duration.ofMillis(TTL_MILLIS), nowMillis, tokenSupplier);
    }

    public ConfirmationStore(Duration lifetime,
                             LongSupplier nowMillis,
                             Supplier<String> tokenSupplier) {
        if(nowMillis == null || tokenSupplier == null){
            throw new IllegalArgumentException(
                    "confirmation clock and token supplier cannot be null"
            );
        }
        if(lifetime == null || lifetime.isZero() || lifetime.isNegative()){
            throw new IllegalArgumentException("confirmation lifetime must be positive");
        }
        this.nowMillis = nowMillis;
        this.tokenSupplier = tokenSupplier;
        this.ttlMillis = validatedLifetime(lifetime);
    }

    public synchronized List<ConfirmationOption> issue(
            String actorId,
            String sessionId,
            String candidateFingerprint,
            String placementPlanFingerprint,
            long snapshotRevision,
            List<PlacementOption> options){
        requireText(actorId, "actor ID");
        requireText(sessionId, "session ID");
        requireText(candidateFingerprint, "candidate fingerprint");
        requireText(placementPlanFingerprint, "placement plan fingerprint");
        if(snapshotRevision < 0L){
            throw new IllegalArgumentException("snapshot revision cannot be negative");
        }
        if(options == null || options.isEmpty()){
            throw new IllegalArgumentException(
                    "confirmation requires at least one placement option"
            );
        }
        invalidateSession(actorId, sessionId);
        long expiresAt = Math.addExact(nowMillis.getAsLong(), ttlMillis);
        ArrayList<ConfirmationOption> result = new ArrayList<>();
        for(PlacementOption option : options){
            if(option == null || option.choice() == SaveChoice.DIRECT){
                throw new IllegalArgumentException(
                        "confirmation options must be non-direct choices"
                );
            }
            String token = uniqueToken();
            confirmations.put(token, new StoredConfirmation(
                    actorId.trim(),
                    sessionId.trim(),
                    candidateFingerprint.trim(),
                    placementPlanFingerprint.trim(),
                    snapshotRevision,
                    option,
                    expiresAt
            ));
            result.add(new ConfirmationOption(token, option, expiresAt));
        }
        return List.copyOf(result);
    }

    public synchronized ConfirmationConsumption consume(
            String token,
            String actorId,
            String sessionId,
            String candidateFingerprint){
        if(isBlank(token)){
            return result(ConfirmationConsumeStatus.INVALID);
        }
        StoredConfirmation stored = confirmations.get(token.trim());
        if(stored == null){
            return result(ConfirmationConsumeStatus.INVALID);
        }
        if(nowMillis.getAsLong() >= stored.expiresAtMillis){
            invalidateSession(stored.actorId, stored.sessionId);
            return result(ConfirmationConsumeStatus.EXPIRED);
        }
        if(!stored.actorId.equals(trim(actorId))
                || !stored.sessionId.equals(trim(sessionId))
                || !stored.candidateFingerprint.equals(trim(candidateFingerprint))){
            return result(ConfirmationConsumeStatus.BINDING_MISMATCH);
        }
        invalidateSession(stored.actorId, stored.sessionId);
        return new ConfirmationConsumption(
                ConfirmationConsumeStatus.AUTHORIZED,
                Optional.of(new ConfirmationAuthorization(
                        stored.placementPlanFingerprint,
                        stored.snapshotRevision,
                        stored.option
                ))
        );
    }

    public synchronized void invalidateSession(String actorId, String sessionId){
        String normalizedActor = trim(actorId);
        String normalizedSession = trim(sessionId);
        Iterator<Map.Entry<String, StoredConfirmation>> iterator =
                confirmations.entrySet().iterator();
        while(iterator.hasNext()){
            StoredConfirmation stored = iterator.next().getValue();
            if(stored.actorId.equals(normalizedActor)
                    && stored.sessionId.equals(normalizedSession)){
                iterator.remove();
            }
        }
    }

    public synchronized int size(){
        return confirmations.size();
    }

    public synchronized void invalidateAll(){
        confirmations.clear();
    }

    public synchronized void setLifetime(Duration lifetime){
        ttlMillis = validatedLifetime(lifetime);
        confirmations.clear();
    }

    private static long validatedLifetime(Duration lifetime){
        if(lifetime == null || lifetime.isZero() || lifetime.isNegative()){
            throw new IllegalArgumentException("confirmation lifetime must be positive");
        }
        long millis = lifetime.toMillis();
        if(millis <= 0L){
            throw new IllegalArgumentException(
                    "confirmation lifetime must be at least one millisecond"
            );
        }
        return millis;
    }

    private String uniqueToken(){
        for(int attempt = 0; attempt < 100; attempt++){
            String token = tokenSupplier.get();
            if(isBlank(token)){
                throw new IllegalStateException(
                        "confirmation token supplier returned a blank token"
                );
            }
            String normalized = token.trim();
            if(!confirmations.containsKey(normalized)){
                return normalized;
            }
        }
        throw new IllegalStateException(
                "confirmation token supplier repeatedly returned duplicates"
        );
    }

    private ConfirmationConsumption result(ConfirmationConsumeStatus status){
        return new ConfirmationConsumption(status, Optional.empty());
    }

    private static void requireText(String value, String label){
        if(isBlank(value)){
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }

    private static boolean isBlank(String value){
        return value == null || value.trim().isEmpty();
    }

    private static String trim(String value){
        return value == null ? "" : value.trim();
    }

    private record StoredConfirmation(String actorId,
                                      String sessionId,
                                      String candidateFingerprint,
                                      String placementPlanFingerprint,
                                      long snapshotRevision,
                                      PlacementOption option,
                                      long expiresAtMillis) {
    }
}
