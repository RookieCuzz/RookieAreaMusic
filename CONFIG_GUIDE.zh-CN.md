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

actions:
  commands:
    enabled: true

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

`checkPeriod` 固定为 20 ticks。`actions.commands.enabled` 是全部区域入场/离场命令的总开关；关闭期间不会执行或排队命令，之后重新开启也不会为仍在区域内的玩家补执行。频道名允许小写字母、数字、点、下划线和连字符。

可新增频道，例如最多两层的地下环境音：

```yaml
  ambience.cave:
    mode: additive
    maxLayers: 2
    trigger: continuous
```

## 4. 创建 BGM 区域

在游戏内执行 `/am area create <区域ID>`，用五件 ROI 工具勾画后完成。命令执行位置不决定高度：每张新切片第一次右键的方块 `blockY` 才是该层 Y，后续顶点只读取 X/Z。普通使用“下一切片”会复制轮廓，潜行使用则创建空白轮廓；潜行后若选择已有 Y，会从空白重画并在保存时替换该层。编辑已有切片时保留原 Y。切换到更高的已有切片时，先使用“下一切片”，再右键该切片 Y 上的方块。新区域默认使用 `bgm` 频道。

`blockY` 是被点击方块自身的原始高度，不是方块顶面或玩家脚部高度；最后一张切片仅覆盖 `[blockY, blockY + 1)`。若要让站在地面上的玩家命中，请按脚部实际 Y 选择切片（必要时放置临时标记方块）或添加更高切片。

保存后，建议先站在区域所在世界执行：

```text
/am area show world <区域ID>
```

该命令会用仅自己可见的粒子标出已保存区域的整个轮廓。请重点确认最低切片覆盖玩家站立时的脚部 Y，并检查最高切片与各层水平边界。再次执行同一命令可关闭预览。

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
  "enterCommands": [
    "title {player} title {\"text\":\"Boss 即将出现\",\"color\":\"dark_red\"}",
    "tag {player} add discovered_boss_gate"
  ],
  "exitCommands": [
    "tag {player} remove discovered_boss_gate",
    "effect clear {player} minecraft:glowing"
  ],
  "shape": { "type": "sliced_polygon", "slices": [] }
}
```

实际使用时 `slices` 必须包含有效 Polygon，可复制 [boss_gate](examples/worlds/world/regions/boss_gate/) 示例。玩家一直留在区域中不会重复触发；至少一条入场命令成功派发后，走出、传送离开、切换世界或重载后区域不再命中会执行一次配对 `exitCommands`，离开后再次进入才会重播声音并重新执行入场命令。玩家掉线或插件停服默认不执行离场命令。

`enterCommands`、`exitCommands` 只能用于 `trigger: enter_once` 的频道。命令以服务端控制台身份在主线程按数组顺序执行，每个字符串只对应一条命令；不会把分号、`&&` 或管道拆成多条命令。推荐省略开头的 `/`，也允许至多写一个前导 `/`。

默认 `stinger.maxLayers: 2` 时，重叠区域中只有实际入选的前两层会执行命令。某次物理进入真正入选后，只有至少一条 `enterCommands` 获得 Bukkit 的成功派发结果，插件才登记 activation token；随后离开时消费同一 token，执行进入时冻结的 `exitCommands`。如果所有入场命令都失败或被跳过，就没有配对离场动作；`exitCommands` 不是独立的离开监听器。被层数限制压制的区域既没有入场命令，也没有离场命令，并且不会在空位出现时延迟触发，必须先离开再进入。命令不依赖声音：把 `music.json` 留空即可创建 command-only 区域。声音循环、重载后仍命中同 UUID 区域、CraftEngine 资源包就绪后的声音补播，都不会重复执行入场命令；某条命令失败时不会重试，并会继续处理后续命令。

离场命令复用全部占位符。位置类占位符（`{world}`、`{x}`、`{block_x}` 等）取该组命令在主线程开始派发时的玩家当前位置；通常是走出或传送后的新位置，但快速连续移动时不保证是第一次越界的落点。`{area_world}` / `%area_world%` 始终表示触发区域所属世界。若 `/am reload` 删除区域或改动形状使玩家不再命中，插件把它视为逻辑离开，并使用入场时冻结的 `exitCommands` 做清理；同 UUID 区域在重载后仍命中则不会离场或重复入场。

无需安装其他插件即可使用内置占位符，例如：

```text
effect give {player} minecraft:glowing 5 0 true
tag {player} add visited_{area_id}
```

内置占位符提供 `{player}`、`{player_name}`、`{player_uuid}`、`{world}`、`{area_world}`、`{area}`、`{area_id}`、`{area_uuid}`、`{x}`、`{y}`、`{z}`、`{block_x}`、`{block_y}`、`{block_z}`，也支持相应的 `%player%`、`%player_name%`、`%player_uuid%`、`%world%`、`%area_world%`、`%area%`、`%area_id%`、`%area_uuid%`、`%x%`、`%y%`、`%z%`、`%block_x%`、`%block_y%`、`%block_z%` 写法。

安装 PlaceholderAPI 和所需扩展后，还可使用其他插件提供的占位符，例如：

```text
broadcast %player_name% 已以 %luckperms_primary_group% 身份进入 Boss 区域
```

没有安装 PlaceholderAPI 时，内置占位符仍然有效。其他 `%...%` 占位符若没有对应扩展、展开失败或仍未解析，该条命令会被跳过，不会把占位符原样发送给目标插件。

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
5. 检查 Stinger 的入场命令只执行一次；走出后检查配对离场命令只执行一次。在区域内执行 `/am reload` 或触发资源包声音补播时不应重复执行。
6. 移动到固定音源四周，确认距离衰减和左右/前后方向。

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

### 进入或离开区域后命令没有执行

- 区域频道必须使用 `trigger: enter_once`。
- 区域可能被 `maxLayers` 限制压制，并未真正入选层位。
- 只有已经入选、且至少一条入场命令成功派发并登记 activation token 的那次进入，才会在随后离开时执行 `exitCommands`；掉线和停服默认不会执行。
- 命令含有未安装扩展提供的 PlaceholderAPI 占位符，因此被安全跳过。
- `enterCommands` 与 `exitCommands` 分别最多 16 条命令；每条最多 1024 个字符，每个数组合计最多 8192 个字符，且不能包含换行或 NUL 控制字符。
- 控制台会记录命令索引和失败原因。失败命令不会自动重试，以免其他插件已经执行了部分效果。
