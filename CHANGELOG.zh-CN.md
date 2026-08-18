# 更新日志

## 2.0.0-SNAPSHOT - 第三方 API

- 新增 `/rr region create global`：无需选点，直接保存覆盖当前整个世界的唯一
  `__global__` 区域，并在 `/rr region list` 中显示 Global。
- 新增独立 `api` classifier JAR，第三方插件以 `provided` 方式依赖。
- 新增 API 语义版本、capability 检测与固定 Snapshot 的统一保护判定。
- 新增 `onLoad()` 阶段 typed Flag、只读 RegionProvider 注册服务；注册表在严格
  JSON staging 前冻结。
- 新增稳定的 `RegionMutationApi`、create/edit request builder 和异步 delete 结果。
- 新增 Bukkit 事件、线程约束、Nexus 发布方式及可独立编译的接入示例。

## 2.0.0 - 2026-08-17

2.0 是一次破坏性重构：插件名称、主类、命令、权限和磁盘格式均已切换为
RookieRegions。它不会读取或自动迁移 RookieAreaMusic 1.x 的 `/am` 命令、
`area.json` / `music.json` 双文件区域、固定坐标音源或旧数据目录。

### 区域核心

- 面向 Paper 1.21.4+ 与 Java 21，提供 Cuboid、Polygon Prism、Sliced Polygon
  和显式 Global Shape；Sliced Polygon 独立保存 `minY` / `maxY`，且首切片从
  `minY` 开始。
- 每个已加载世界拥有一个 `__global__` 根区域；普通区域必须指向同世界 parent，
  并被 parent 严格包含。允许共享边界，但拒绝相等或越界的父子形状。
- 使用世界 UUID 与大小写无关的区域 ID 作为稳定身份；支持任意层级 parent、
  priority、owners、members 与 groups。
- 使用不可变 `RegionSnapshot`、父子图和 Chunk 空间索引提供同步保护查询。
- 精确区分 `DISJOINT`、`TOUCHING`、`INSIDE`、`CONTAINS`、`EQUAL` 与
  `OVERLAP`；只共享边界不算正体积冲突。

### 创建、编辑与覆盖确认

- 新增 `/rr` 命令树与木斧编辑器，支持 cuboid、polygon 和逐层 sliced 草稿。
- 每名玩家只能持有一个编辑 session，同一区域同时只允许一名玩家编辑。
- 普通玩家可直接创建荒野区域；在现有区域内创建子区时，候选 parent 必须本地显式
  设置 `core.allow-player-regions: allow`。
- 部分重叠、相等或包住现有区域的创建默认拒绝；拥有
  `rookieregions.region.overlap` 的管理员可通过一次性确认保留覆盖或选择 parent。
- 确认 token 绑定玩家、session、候选指纹、放置方案与 snapshot revision；过期、
  重放或候选变化都会拒绝。等待确认或保存失败时继续持锁，成功保存或显式 cancel
  才释放。

### 强类型 Flag 与保护

- 内置 `build`、`block-break`、`block-place`、`use`、`container`、
  `pvp`、`entry`、`explosion` 和 `core.allow-player-regions`。
- Flag 值按注册 codec 保持 JSON 类型；当前内置 Flag 使用 `allow` / `deny`
  State。未知 Flag 或错误类型不再被静默忽略。
- 父子链按最近显式值继承；互不相关的重叠分支先比较 priority，同 priority 的
  State 冲突由 `DENY` 胜出。
- Owner/member 默认可建造、使用与打开容器，非成员默认拒绝；PvP、entry 与
  explosion 默认允许。显式拒绝仅能由对应 bypass 权限绕过。

### 原子持久化

- 数据路径改为
  `plugins/RookieRegions/worlds/<world-uuid>/regions/<region-id>.json`。
- 每个区域只有一个 `schemaVersion: 1` JSON 文档，原子包含核心区域、Flag、
  Music 与 Commands 附件。
- 严格拒绝重复 key、未知字段、未知模块、未知 Shape、未知 Flag、注释、尾逗号、
  `NaN` / `Infinity`、缺失字段和非法父子图；错误包含文件路径与 JSON Pointer。
- reload 先完整 staging 所有已加载世界，全部验证成功后才发布单一快照；任一文件
  失败时旧快照继续运行。
- 保存使用同目录临时文件、`FileChannel.force` 与 `ATOMIC_MOVE`，不降级为
  非原子替换；删除移动到插件根目录的 `.trash/<world-uuid>/`。
- 缺失 `__global__.json` 时自动合成空 global，priority 为
  `Integer.MIN_VALUE`。

### 模块

- Music 成为区域附件，并按频道明确使用 `INHERIT`、`ADD`、`REPLACE` 或
  `BLOCK`。频道可在 `config.yml` 中配置为 `EXCLUSIVE` 或 `LAYERED`。
- 父链严格按 Global→父→子应用：`ADD` 保留祖先层并增加本区层，`REPLACE` 清空祖先
  后替换，`BLOCK` 清空父链结果，`INHERIT` 保持不变；order 只排序无关重叠分支，
  BLOCK 的阻断下限不会压制自己的 descendant。
- Commands 附件使用 `enter` / `leave` 数组，根据物理区域成员变化以控制台身份
  执行，并提供 `{player}`、`{uuid}`、`{region}` 占位符。

### API 与兼容

- 通过 Bukkit `ServicesManager` 发布 RookieRegions API、Flag Registry、固定快照
  查询和 Provider 注册能力。
- 可选 WorldGuard Provider 只读映射 WorldGuard 区域；RookieRegions 原生区域的
  持久化与写事务仍由本插件负责。
- Music/Commands profile 新增强类型 provider binding；默认 Native，自带命令可绑定
  WorldGuard region ID。查询钉住 provider snapshot 与 ID 映射，失败保留 last-good；
  非包含的 WorldGuard 逻辑 parent 展开到 Global 并输出诊断。
- CraftEngine 仍可用于提供资源包 Sound Event，但 2.0 不再包含 1.x 固定坐标音源
  文件格式。

## 1.x - RookieAreaMusic（历史版本）

1.x 提供 CT ROI 编辑器、多频道区域音乐、Stinger、入离场命令和固定坐标音源。
这些功能使用 `/am`、`plugins/RookieAreaMusic/` 以及 `area.json` / `music.json`
格式，仅适用于旧版插件，不是 2.0 的兼容输入。
