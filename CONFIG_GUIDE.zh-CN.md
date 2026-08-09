# RookieAreaMusic 配置实操指南

本文解决“怎样把一批 OGG 配成可用的区域音乐”。完整字段定义和运行语义见 [CONFIG_FORMAT.zh-CN.md](CONFIG_FORMAT.zh-CN.md)。

## 1. 规划声音键

建议为不同用途保留稳定前缀：

```text
rookie_music:bgm.*        主背景音乐
rookie_music:ambience.*   可叠加环境层
rookie_music:stinger.*    进入区域时播放一次
rookie_music:source.*     固定坐标音源
```

Sound Event 与 OGG 路径是两件事。例如：

```text
Sound Event: rookie_music:ambience.forest_wind
OGG 路径:    assets/rookie_music/sounds/ambience/forest_wind.ogg
CraftEngine name: rookie_music:ambience/forest_wind
```

以后替换 OGG 时保持 Sound Event 不变，RookieAreaMusic 的区域配置就无需跟着改名。

## 2. 部署 CraftEngine 声音包

复制 [examples/craftengine/rookie_music](examples/craftengine/rookie_music/) 到：

```text
plugins/CraftEngine/resources/rookie_music/
```

得到以下结构：

```text
plugins/CraftEngine/resources/rookie_music/
├─ pack.yml
├─ configuration/
│  └─ sounds.yml
└─ resourcepack/assets/rookie_music/sounds/
   ├─ music/
   ├─ ambience/
   ├─ stinger/
   └─ source/
```

把自己的 OGG 放入对应目录。示例 YAML 中使用的文件名是占位名称，必须提供同名文件或修改 `name`。

执行：

```text
/ce reload all
```

随后按 CraftEngine 当前的托管方式重新生成/下发资源包，让客户端完成加载。CraftEngine 只负责声音资源；RookieAreaMusic 不读取 OGG，也不会自动上传资源包。

### CraftEngine 条目怎么选

- 长 BGM、长环境底噪：`stream: true`，减少整段音频一次性载入内存。
- 短 Stinger、鸟叫、机械声：通常不写 `stream`；需要登录时预热可使用 `preload: true`。
- `attenuation_distance`：CraftEngine 源码支持下划线或连字符写法，模板统一使用下划线。
- `weight`：同一 Sound Event 下放多份 OGG 时控制随机权重。
- `volume` / `pitch`：这是资源包文件层修正；RookieAreaMusic 的区域/音源配置还会再应用一次播放音量与音高。

区域音乐开始播放时以玩家当时位置为声源，因此长音乐建议给予较大的 `attenuation_distance`。固定音源需要方向与距离感，应使用较小的衰减距离和单声道 OGG。

## 3. 配置频道

默认 `plugins/RookieAreaMusic/config.yml`：

```yaml
engine:
  checkPeriod: 20

channels:
  bgm:
    mode: exclusive
    maxLayers: 1
    trigger: continuous
  ambience:
    mode: additive
    maxLayers: 3
    trigger: continuous
  stinger:
    mode: additive
    maxLayers: 2
    trigger: enter_once
```

`checkPeriod` 固定为 20 ticks。频道名允许小写字母、数字、点、下划线和连字符。

可新增频道，例如最多两层的地下环境音：

```yaml
  ambience.cave:
    mode: additive
    maxLayers: 2
    trigger: continuous
```

## 4. 创建 BGM 区域

在游戏内执行 `/am area create <区域ID>`，用五件 ROI 工具勾画后完成。新区域默认使用 `bgm` 频道。

添加曲目：

```text
/am music add world spawn day rookie_music:bgm.overworld_day 180
/am music add world spawn night rookie_music:bgm.overworld_night 165
```

默认 `random: false` 时按配置顺序轮换；`random: true` 时随机选择。`loop: true` 表示一首到期后继续选下一首。

若直接编辑文件，目录为：

```text
plugins/RookieAreaMusic/worlds/world/regions/spawn/
├─ area.json
└─ music.json
```

## 5. 配置三层 Ambience

同一个森林范围建立三个区域，几何可以完全相同：

```text
forest_wind   channel=ambience  order=30
forest_birds  channel=ambience  order=20
forest_water  channel=ambience  order=10
```

每个区域的 `music.json` 只放自己的声音。进入重叠范围时三层一起播放；如果有第四个 `ambience` 候选，默认只保留排序最高的三层。

可直接参考：

- [forest_wind](examples/worlds/world/regions/forest_wind/)
- [forest_birds](examples/worlds/world/regions/forest_birds/)
- [forest_water](examples/worlds/world/regions/forest_water/)

调音时建议从较低音量开始，例如风 `0.35`、鸟 `0.55`、水 `0.45`，避免三层叠加后过响。

## 6. 配置 Stinger

Stinger 只在玩家由区域外进入区域内时触发：

```json
{
  "channel": "stinger",
  "order": 0,
  "priority": "HIGH",
  "random": false,
  "loop": false,
  "enabled": true,
  "overwrite": false,
  "volume": 1.0,
  "pitch": 1.0,
  "shape": { "type": "sliced_polygon", "slices": [] }
}
```

实际使用时 `slices` 必须包含有效 Polygon，可复制 [boss_gate](examples/worlds/world/regions/boss_gate/) 示例。玩家一直留在区域中不会重复触发；离开后再次进入才会重播。

## 7. 配置固定坐标音源

固定音源不属于区域频道，文件位置是：

```text
plugins/RookieAreaMusic/worlds/<世界>/sources/<音源ID>.json
```

瀑布示例：

```json
{
  "position": { "x": 160.5, "y": 68.5, "z": 152.5 },
  "sound": "rookie_music:source.waterfall",
  "duration": 24,
  "interval": 0,
  "volume": 1.5,
  "pitch": 1.0,
  "enabled": true
}
```

调度周期为 `duration + interval`。`interval: 0` 表示声音结束后立即衔接下一次。多个同键固定音源会从各自坐标独立播放。

## 8. 重载与验收

推荐顺序：

1. 修改 OGG 或 `sounds.yml`：`/ce reload all`，重新下发资源包。
2. 修改 RookieAreaMusic JSON 或频道：`/am reload`。
3. 进入区域，分别验证 BGM、三层 Ambience、Stinger 和固定音源。
4. 离开区域确认区域声音停止，再次进入确认 Stinger 重触发。
5. 移动到固定音源四周，确认距离衰减和左右/前后方向。

重载失败时查看控制台中的具体文件路径。插件只有在全部新配置验证成功后才替换运行时快照，失败不会把半套配置投入运行。

## 9. 常见问题

### 有字幕提示但听不到声音

- 客户端资源包尚未成功加载。
- RookieAreaMusic 中写的是 OGG 路径，而不是 Sound Event。
- `sounds.yml` 的 `name` 与实际 OGG 大小写或目录不一致。
- OGG 编码不受 Minecraft 支持，建议转为标准 Ogg Vorbis。

### 音乐过早重播或中间有空白

校准 RookieAreaMusic 的 `duration`，它必须接近 OGG 的实际秒数。CraftEngine 的 Sound Event 不会把时长传给插件。

### 固定音源没有方向感

把素材转成单声道，并检查 `attenuation_distance` 没有设置得过大。立体声素材更适合 BGM，不适合定位鸟叫或瀑布。

### 三层 Ambience 只听到一层

确认三个区域都使用 `channel: ambience`、几何实际重叠、曲目事件键不同，并检查 `channels.ambience.maxLayers` 至少为 3。
