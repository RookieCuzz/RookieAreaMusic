package io.github.rookiecuzz.rookieareamusic.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionMusicConfig {
    @Builder.Default
    private List<Track> music = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Track {
        private String id;
        private String sound;
        private Long duration;
    }
}
