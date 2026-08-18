package io.github.rookiecuzz.rookieregions.module.music;

import io.github.rookiecuzz.rookieregions.runtime.ModuleRegionBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure parser from JSON/YAML-style maps into immutable music models. */
public final class MusicModuleParser {
    public RegionMusicProfile parseProfile(Map<String, ?> raw){
        if(raw == null || raw.isEmpty()){
            return RegionMusicProfile.empty();
        }
        Map<String, ?> channels = requireMap(raw.get("channels"), "music.channels");
        Map<String, RegionMusicChannel> parsed = new LinkedHashMap<>();
        for(Map.Entry<String, ?> entry : channels.entrySet()){
            String channel = RegionMusicProfile.requireKey(
                    entry.getKey(), "music channel"
            );
            parsed.put(channel, parseRegionChannel(
                    requireMap(entry.getValue(), "music.channels." + channel),
                    "music.channels." + channel
            ));
        }
        return new RegionMusicProfile(
                parsed,
                parseBinding(raw.get("binding"), "music.binding")
        );
    }

    public Map<String, MusicChannelDefinition> parseChannelDefinitions(
            Map<String, ?> raw){
        if(raw == null || raw.isEmpty()){
            return Collections.emptyMap();
        }
        Map<String, MusicChannelDefinition> parsed = new LinkedHashMap<>();
        for(Map.Entry<String, ?> entry : raw.entrySet()){
            String channel = RegionMusicProfile.requireKey(
                    entry.getKey(), "channel name"
            );
            Map<String, ?> values = requireMap(
                    entry.getValue(), "channels." + channel
            );
            ChannelPlaybackMode mode = ChannelPlaybackMode.parse(
                    requireString(values.get("mode"), "channels." + channel + ".mode")
            );
            int maxLayers;
            if(mode == ChannelPlaybackMode.EXCLUSIVE){
                maxLayers = optionalInt(
                        values.get("maxLayers"), 1,
                        "channels." + channel + ".maxLayers"
                );
            } else {
                maxLayers = requireInt(
                        values.get("maxLayers"),
                        "channels." + channel + ".maxLayers"
                );
            }
            parsed.put(channel, new MusicChannelDefinition(
                    channel, mode, maxLayers
            ));
        }
        return Collections.unmodifiableMap(parsed);
    }

    private RegionMusicChannel parseRegionChannel(Map<String, ?> raw,
                                                  String path){
        MusicPolicyMode policy = MusicPolicyMode.parse(
                requireString(raw.get("policy"), path + ".policy")
        );
        List<MusicTrack> tracks = parseTracks(raw.get("tracks"), path + ".tracks");
        return RegionMusicChannel.builder()
                .policy(policy)
                .order(optionalInt(raw.get("order"), 0, path + ".order"))
                .random(optionalBoolean(raw.get("random"), false, path + ".random"))
                .loop(optionalBoolean(raw.get("loop"), true, path + ".loop"))
                .volume(optionalFloat(raw.get("volume"), 1.0f, path + ".volume"))
                .pitch(optionalFloat(raw.get("pitch"), 1.0f, path + ".pitch"))
                .overwrite(optionalBoolean(
                        raw.get("overwrite"), true, path + ".overwrite"
                ))
                .tracks(tracks)
                .build();
    }

    private List<MusicTrack> parseTracks(Object raw, String path){
        if(raw == null){
            return Collections.emptyList();
        }
        if(!(raw instanceof List<?>)){
            throw new IllegalArgumentException(path + " must be a list");
        }
        List<MusicTrack> result = new ArrayList<>();
        List<?> values = (List<?>) raw;
        for(int index = 0; index < values.size(); index++){
            String trackPath = path + "[" + index + "]";
            Map<String, ?> track = requireMap(values.get(index), trackPath);
            result.add(new MusicTrack(
                    requireString(track.get("id"), trackPath + ".id"),
                    requireString(track.get("sound"), trackPath + ".sound"),
                    requireLong(track.get("duration"), trackPath + ".duration")
            ));
        }
        return result;
    }

    private Map<String, ?> requireMap(Object value, String path){
        if(!(value instanceof Map<?, ?>)){
            throw new IllegalArgumentException(path + " must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for(Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()){
            if(!(entry.getKey() instanceof String)){
                throw new IllegalArgumentException(path + " keys must be strings");
            }
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String requireString(Object value, String path){
        if(!(value instanceof String) || ((String) value).trim().isEmpty()){
            throw new IllegalArgumentException(path + " must be a non-blank string");
        }
        return ((String) value).trim();
    }

    private boolean optionalBoolean(Object value,
                                    boolean fallback,
                                    String path){
        if(value == null){
            return fallback;
        }
        if(!(value instanceof Boolean)){
            throw new IllegalArgumentException(path + " must be a boolean");
        }
        return (Boolean) value;
    }

    private int optionalInt(Object value, int fallback, String path){
        return value == null ? fallback : requireInt(value, path);
    }

    private int requireInt(Object value, String path){
        long parsed = requireLong(value, path);
        if(parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE){
            throw new IllegalArgumentException(path + " is outside the integer range");
        }
        return (int) parsed;
    }

    private long requireLong(Object value, String path){
        if(!(value instanceof Number)){
            throw new IllegalArgumentException(path + " must be an integer");
        }
        Number number = (Number) value;
        double asDouble = number.doubleValue();
        long asLong = number.longValue();
        if(Double.isNaN(asDouble) || Double.isInfinite(asDouble)
                || asDouble != (double) asLong){
            throw new IllegalArgumentException(path + " must be an integer");
        }
        return asLong;
    }

    private float optionalFloat(Object value, float fallback, String path){
        if(value == null){
            return fallback;
        }
        if(!(value instanceof Number)){
            throw new IllegalArgumentException(path + " must be a number");
        }
        float parsed = ((Number) value).floatValue();
        if(Float.isNaN(parsed) || Float.isInfinite(parsed)){
            throw new IllegalArgumentException(path + " must be finite");
        }
        return parsed;
    }

    private ModuleRegionBinding parseBinding(Object value, String path) {
        if(value == null) {
            return ModuleRegionBinding.nativeSelf();
        }
        Map<String, ?> binding = requireMap(value, path);
        return ModuleRegionBinding.toProvider(
                requireString(binding.get("provider"), path + ".provider"),
                requireString(binding.get("region"), path + ".region")
        );
    }
}
