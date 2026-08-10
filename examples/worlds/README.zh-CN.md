# RookieAreaMusic 配置示例

把需要的世界目录复制到 `plugins/RookieAreaMusic/worlds/`，再执行 `/am reload`。

`world/regions/` 包含：

- `spawn`：普通 BGM 区域。
- `forest_wind`、`forest_birds`、`forest_water`：三个几何重叠的 Ambience 区域，可同时播放。
- `boss_gate`：进入一次的 Stinger 区域，同时示范使用 `{player}` 执行标题、玩家标签及配对离场清理命令。

`world/sources/` 包含树上鸟叫、瀑布和篝火三个固定坐标音源。

所有 Sound Event 与 `examples/craftengine/rookie_music/configuration/sounds.yml` 对应。坐标只是示例，复制到正式服前请替换几何和音源位置。

`boss_gate/area.json` 的 `enterCommands` 与 `exitCommands` 由控制台在主线程按顺序执行。只有实际入选 Stinger 层位、且至少一条入场命令成功派发的物理进入才会登记 activation token，并在玩家随后走出、传送离开、切换世界或配置重载后不再命中时执行一次进入时冻结的配对离场命令；被压制的区域、所有入场命令均失败的进入、玩家掉线和停服都不会执行离场命令。离场命令的 `{world}` 与位置占位符取该组命令开始派发时的玩家当前位置，`{area_world}` 取区域所属世界。示例只使用无需依赖的内置占位符；若已安装 PlaceholderAPI 及对应扩展，也可加入例如 `broadcast %player_name%（%luckperms_primary_group%）发现了 Boss 区域` 的命令。未解析的外部占位符会使该条命令被跳过。
