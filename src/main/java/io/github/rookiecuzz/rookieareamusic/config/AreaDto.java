package io.github.rookiecuzz.rookieareamusic.config;

import io.github.rookiecuzz.rookieareamusic.geometry.SlicedPolygonVolume;
import lombok.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class AreaDto {
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class Point{
        private Double x;
        private Double y;
        private Double z;
    }

    // 区域所在世界 ID
    private String world;
    // 区域 ID
    private String uuid;
    // 区域可读 ID
    private String areaId;
    // 要使用的音乐 ID 列表
    private List<String> musicId;
    // 播放频道
    private String channel;
    // 同优先级下的排序值，数值越大越优先
    private Integer order;
    // 区域优先级
    private Priority priority;
    // 是否启用随机选取音乐，如果否则顺序选取
    private Boolean random;
    // 是否进行多次播放，如果只有一首歌则循环该歌曲，如果存在多首歌则随机选取后继续播放
    private Boolean loop;
    // 是否启用
    private Boolean enabled;
    // 是否覆盖用户当前正在播放的音乐（该覆盖只会在用户进入区域的时候被执行）
    private Boolean overWrite;
    // 音量 (0.0-1.0)
    private Float volume;
    // 速度 (0.0-2.0)
    private Float pitch;
    @Builder.Default
    private List<String> enterCommands = new CopyOnWriteArrayList<>();
    @Builder.Default
    private List<String> exitCommands = new CopyOnWriteArrayList<>();
    // CT 式多切片 Polygon 体积
    private SlicedPolygonVolume shape;
    // 起始点
    private Point minPoint;
    // 终止点
    private Point maxPoint;

    public void setEnterCommands(List<String> enterCommands){
        this.enterCommands = enterCommands == null
                ? new CopyOnWriteArrayList<>()
                : new CopyOnWriteArrayList<>(enterCommands);
    }

    public void setExitCommands(List<String> exitCommands){
        this.exitCommands = exitCommands == null
                ? new CopyOnWriteArrayList<>()
                : new CopyOnWriteArrayList<>(exitCommands);
    }
}
