package io.github.rookiecuzz.rookieareamusic.config;

import com.google.gson.annotations.SerializedName;
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
public class RegionAreaConfig {
    private String channel;
    private Integer order;
    private Priority priority;
    private Boolean random;
    private Boolean loop;
    private Boolean enabled;
    @SerializedName(value = "overwrite", alternate = {"overWrite"})
    private Boolean overWrite;
    private Float volume;
    private Float pitch;
    @Builder.Default
    private List<String> enterCommands = new ArrayList<>();
    @Builder.Default
    private List<String> exitCommands = new ArrayList<>();
    private RegionShapeConfig shape;
}
