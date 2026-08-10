package io.github.rookiecuzz.rookieareamusic.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class PlaybackChannelRegistry {
    public static final String DEFAULT_CHANNEL = "bgm";
    private static final Pattern CHANNEL_NAME =
            Pattern.compile("[a-z0-9._-]+");

    private final Map<String, PlaybackChannelConfig> channels;

    private PlaybackChannelRegistry(Map<String, PlaybackChannelConfig> channels) {
        this.channels = channels;
    }

    public static PlaybackChannelRegistry defaults(){
        Map<String, PlaybackChannelConfig> defaults = new LinkedHashMap<>();
        defaults.put("bgm", channel(
                ChannelMode.EXCLUSIVE,
                1,
                ChannelTrigger.CONTINUOUS
        ));
        defaults.put("ambience", channel(
                ChannelMode.ADDITIVE,
                3,
                ChannelTrigger.CONTINUOUS
        ));
        defaults.put("stinger", channel(
                ChannelMode.ADDITIVE,
                2,
                ChannelTrigger.ENTER_ONCE
        ));
        return of(defaults);
    }

    public static PlaybackChannelRegistry of(
            Map<String, PlaybackChannelConfig> source){
        if(source == null || source.isEmpty()){
            throw new IllegalArgumentException("至少需要配置一个播放频道");
        }

        Map<String, PlaybackChannelConfig> validated = new LinkedHashMap<>();
        for(Map.Entry<String, PlaybackChannelConfig> entry : source.entrySet()){
            String name = entry.getKey();
            PlaybackChannelConfig config = entry.getValue();
            validate(name, config);
            validated.put(name, copy(config));
        }
        if(!validated.containsKey(DEFAULT_CHANNEL)){
            throw new IllegalArgumentException("必须配置默认频道 bgm");
        }
        return new PlaybackChannelRegistry(Collections.unmodifiableMap(validated));
    }

    public PlaybackChannelConfig require(String channelName){
        PlaybackChannelConfig result = channels.get(channelName);
        if(result == null){
            throw new IllegalArgumentException("未知播放频道: " + channelName);
        }
        return result;
    }

    public boolean contains(String channelName){
        return channels.containsKey(channelName);
    }

    public Map<String, PlaybackChannelConfig> asMap(){
        return channels;
    }

    private static void validate(String name, PlaybackChannelConfig config){
        if(name == null || !CHANNEL_NAME.matcher(name).matches()){
            throw new IllegalArgumentException(
                    "频道名称只能包含小写字母、数字、点、下划线和连字符: " + name
            );
        }
        if(config == null
                || config.getMode() == null
                || config.getTrigger() == null
                || config.getMaxLayers() == null
                || config.getMaxLayers() <= 0){
            throw new IllegalArgumentException("频道配置不完整: " + name);
        }
        if(config.getMode() == ChannelMode.EXCLUSIVE
                && config.getMaxLayers() != 1){
            throw new IllegalArgumentException(
                    "exclusive 频道的 maxLayers 必须为 1: " + name
            );
        }
    }

    private static PlaybackChannelConfig channel(ChannelMode mode,
                                                 int maxLayers,
                                                 ChannelTrigger trigger){
        return PlaybackChannelConfig.builder()
                .mode(mode)
                .maxLayers(maxLayers)
                .trigger(trigger)
                .build();
    }

    private static PlaybackChannelConfig copy(PlaybackChannelConfig source){
        return channel(
                source.getMode(),
                source.getMaxLayers(),
                source.getTrigger()
        );
    }
}
