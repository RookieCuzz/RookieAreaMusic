package com.gitee.niocho.areamusic.config;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private RegionShapeConfig shape;
}
