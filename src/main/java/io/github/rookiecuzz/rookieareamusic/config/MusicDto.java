package io.github.rookiecuzz.rookieareamusic.config;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MusicDto {
    // 音乐唯一 ID
    private String uuid;
    // 音乐标识 ID
    private String musicId;
    // 音乐材质包索引
    private String musicURL;
    // 音乐长度（该配置项异常重要，请一定要准确）
    private Long musicDuration;
}
