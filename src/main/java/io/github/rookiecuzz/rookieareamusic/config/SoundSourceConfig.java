package io.github.rookiecuzz.rookieareamusic.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** JSON representation of one worlds/<world>/sources/<id>.json file. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoundSourceConfig {
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Position {
        private Double x;
        private Double y;
        private Double z;
    }

    private Position position;
    private String sound;
    private Long duration;
    private Long interval;
    private Float volume;
    private Float pitch;
    private Boolean enabled;
}
