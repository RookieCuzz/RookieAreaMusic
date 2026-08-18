package io.github.rookiecuzz.rookieregions.module.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/** Pure per-player playback state transition used by the Bukkit adapter. */
final class MusicPlaybackPlanner {
    Plan plan(Map<String, ActiveTrack> previous,
              Map<String, ResolvedMusicLayer> desired,
              long nowNanos,
              IntUnaryOperator randomIndex) {
        Objects.requireNonNull(previous, "previous music state cannot be null");
        Objects.requireNonNull(desired, "desired music layers cannot be null");
        Objects.requireNonNull(randomIndex, "random track selector cannot be null");

        LinkedHashMap<String, ActiveTrack> next = new LinkedHashMap<>();
        ArrayList<Start> starts = new ArrayList<>();
        LinkedHashSet<String> requestedStops = new LinkedHashSet<>();
        LinkedHashSet<String> continuingSounds = new LinkedHashSet<>();

        for(Map.Entry<String, ActiveTrack> entry : previous.entrySet()){
            if(!desired.containsKey(entry.getKey())){
                requestedStops.add(entry.getValue().track().getSound());
            }
        }

        for(Map.Entry<String, ResolvedMusicLayer> entry : desired.entrySet()){
            String playbackKey = entry.getKey();
            ResolvedMusicLayer layer = Objects.requireNonNull(
                    entry.getValue(),
                    "desired music layer cannot be null"
            );
            if(layer.getTracks().isEmpty()){
                continue;
            }
            ActiveTrack current = previous.get(playbackKey);
            boolean matches = current != null && stillMatches(current, layer);
            if(matches
                    && (!current.loop() || nowNanos < current.nextPlayNanos())){
                next.put(playbackKey, current);
                continuingSounds.add(current.track().getSound());
                continue;
            }

            // A scheduled replay may overlap the natural tail unless overwrite
            // was requested. A changed/removed layer always relinquishes its old
            // sound, matching the adapter's previous replacement semantics.
            if(current != null && (!matches || current.overwrite())){
                requestedStops.add(current.track().getSound());
            }
            MusicTrack selected = select(layer, randomIndex);
            ActiveTrack replacement = new ActiveTrack(
                    selected,
                    deadline(nowNanos, selected.getDurationSeconds()),
                    layer.isLoop(),
                    layer.getVolume(),
                    layer.getPitch(),
                    layer.isOverwrite()
            );
            next.put(playbackKey, replacement);
            starts.add(new Start(playbackKey, replacement));
        }

        // Bukkit stops sounds by key/category, not by individual playback.
        // Never stop a sound still owned by another continuing layer.
        requestedStops.removeAll(continuingSounds);
        return new Plan(next, requestedStops, starts);
    }

    private static boolean stillMatches(ActiveTrack active,
                                        ResolvedMusicLayer desired) {
        return desired.getTracks().contains(active.track())
                && active.loop() == desired.isLoop()
                && Float.compare(active.volume(), desired.getVolume()) == 0
                && Float.compare(active.pitch(), desired.getPitch()) == 0
                && active.overwrite() == desired.isOverwrite();
    }

    private static MusicTrack select(ResolvedMusicLayer layer,
                                     IntUnaryOperator randomIndex) {
        List<MusicTrack> tracks = layer.getTracks();
        if(!layer.isRandom()){
            return tracks.getFirst();
        }
        return tracks.get(Math.floorMod(randomIndex.applyAsInt(tracks.size()), tracks.size()));
    }

    private static long deadline(long nowNanos, long durationSeconds) {
        long duration;
        try {
            duration = Math.multiplyExact(durationSeconds, 1_000_000_000L);
        } catch(ArithmeticException exception){
            duration = Long.MAX_VALUE;
        }
        try {
            return Math.addExact(nowNanos, duration);
        } catch(ArithmeticException exception){
            return Long.MAX_VALUE;
        }
    }

    record ActiveTrack(MusicTrack track,
                       long nextPlayNanos,
                       boolean loop,
                       float volume,
                       float pitch,
                       boolean overwrite) {
        ActiveTrack {
            Objects.requireNonNull(track, "active music track cannot be null");
        }
    }

    record Start(String playbackKey, ActiveTrack track) {
        Start {
            Objects.requireNonNull(playbackKey, "playback key cannot be null");
            Objects.requireNonNull(track, "started track cannot be null");
        }
    }

    record Plan(Map<String, ActiveTrack> next,
                Set<String> stopSounds,
                List<Start> starts) {
        Plan {
            next = Collections.unmodifiableMap(new LinkedHashMap<>(next));
            stopSounds = Collections.unmodifiableSet(new LinkedHashSet<>(stopSounds));
            starts = List.copyOf(starts);
        }
    }
}
