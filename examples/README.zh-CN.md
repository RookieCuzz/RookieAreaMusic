# RookieRegions 2.0 示例

`api-plugin/` 是一个可独立构建的第三方接入示例，展示 `onLoad()` 注册 typed Flag、
运行时保护查询和区域事件监听。

把 `worlds/` 复制到 `plugins/RookieRegions/` 后，将示例中的世界 UUID 和
`world.key` 改成服务器实际世界值。区域文件名必须等于规范化后的区域 ID。

- `forest.json`：外层森林区域，在 `ambience` 频道播放鸟鸣。
- `quiet_grove.json`：森林内部的子区域，以 `BLOCK` 清空同频道音乐。

这正是“外层有鸟叫、内部静音”的三级模型：`__global__ → forest → quiet_grove`。
实际使用时不必创建 `__global__.json`；引擎会合成 Global，只有管理员修改它后
才会写入磁盘。

两个示例模块都写有必填的 `"binding": null`，表示 profile 使用自身 Native
区域几何。若只想复用 profile、改由 WorldGuard 区域命中，可把 binding 改为
`{"provider":"worldguard","region":"目标区域id"}`，或用 `/rr module bind`。
