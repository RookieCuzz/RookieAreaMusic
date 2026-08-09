# RookieAreaMusic 配置示例

把需要的世界目录复制到 `plugins/RookieAreaMusic/worlds/`，再执行 `/am reload`。

`world/regions/` 包含：

- `spawn`：普通 BGM 区域。
- `forest_wind`、`forest_birds`、`forest_water`：三个几何重叠的 Ambience 区域，可同时播放。
- `boss_gate`：进入一次的 Stinger 区域。

`world/sources/` 包含树上鸟叫、瀑布和篝火三个固定坐标音源。

所有 Sound Event 与 `examples/craftengine/rookie_music/configuration/sounds.yml` 对应。坐标只是示例，复制到正式服前请替换几何和音源位置。
