# RookieRegions

RookieRegions 是面向 Paper 1.21.4+ 的三维区域、权限保护与效果模块插件。它使用不可变区域快照、Chunk 空间索引、强类型 Flag 和严格父子继承，音乐只是其中一个可选模块。

> 2.0 是破坏性重构，不读取 RookieAreaMusic 的类、命令或数据目录。

## 环境

- Paper 1.21.4+
- Java 21
- 可选：WorldGuard、CraftEngine

构建：

```bash
mvn clean package
```

生成的单插件 JAR 位于 `target/`。

构建还会附加生成供第三方插件以 `provided` 方式依赖的
`RookieRegions-2.0.0-SNAPSHOT-api.jar`。完整接入、注册生命周期、线程约束和示例见
[API.md](API.md)。

## 区域模型

每个世界都有一个合成的 `__global__` 根区域。普通区域保存：

- 大小写无关 ID 与世界 UUID
- Cuboid、Polygon Prism 或 Sliced Polygon 形状
- 任意 `int` priority
- 一个严格包含它的 parent
- owners、members、groups
- 强类型 flags

子区域可以无限嵌套。子区显式 Flag 优先于父区；互不相关的重叠区域再按 priority 处理，同优先级 State Flag 由 `DENY` 胜出。

区域边界接触不算重叠。子区可以贴着父区墙面、地板或顶面，但不能与父区完全相等，也不能越界。

## 玩家与管理员建区

- 有 `rookieregions.region.create` 的玩家可直接创建与其他有限区域不相交或仅接触边界的区域。
- 玩家要在现有区域内创建子区，父区必须本地显式设置 `core.allow-player-regions=allow`。
- 玩家不能创建部分交叠、相等或包住其他区域的区域。
- 有 `rookieregions.region.overlap` 的管理员可以保留这些重叠，但首次保存仍必须点击 30 秒内有效的一次性确认按钮。
- token 绑定玩家、编辑 session、候选几何、父区选择和 snapshot revision；任何变化都会让旧 token 失效。

## 保护默认值

区域内未配置 Flag 时：

| 行为 | Owner/Member | 其他玩家 | 荒野 |
| --- | --- | --- | --- |
| build / break / place | 允许 | 拒绝 | 允许 |
| use / container | 允许 | 拒绝 | 允许 |
| PvP / entry / explosion | 允许 | 允许 | 允许 |

显式 `DENY` 对 owner/member 同样生效；只有对应的 `rookieregions.bypass.*` 权限可以绕过。

## 命令

```text
/rr region create <id> <cuboid|polygon|sliced>
/rr region create global
/rr region edit <id>
/rr region delete <id>
/rr region info <id>
/rr region list
/rr region editor finish [token]
/rr region editor cancel|undo|clear
/rr region editor slice <y>
/rr region editor min-y <y>
/rr region editor max-y <y>
/rr region priority|parent|owner|member|flag ...
/rr music <region> <channel> <inherit|block>
/rr music <region> <channel> <add|replace> <track-id> <sound> <duration-seconds> [order]
/rr module bind <music|commands> <profile-region> <provider> <provider-region>
/rr module unbind <music|commands> <profile-region>
/rr module info <music|commands> <profile-region>
/rr reload
```

`/rr region create global` 不需要木斧选点，会直接把当前世界唯一的
`__global__` 保存为覆盖整个世界的区域。它可以配置 Flag、owner、member、Music
和 Commands，但不能删除、修改 parent，也不会参与普通区域的重叠竞争。

`/rr` 本身没有全局管理员门禁，每个子命令分别检查权限。

## 数据格式

```text
plugins/RookieRegions/
  config.yml
  worlds/<world-uuid>/regions/<region-id>.json
  .trash/<world-uuid>/<region-id>.<timestamp>.<uuid>.json
```

一个 JSON 文档原子保存核心区域和模块附件。加载器拒绝未知 schema、字段、Flag、模块、Shape、枚举、重复 JSON key、NaN/Infinity 和非法父图。reload 任一文件失败时，旧快照继续运行。

## Music 模块

每个写入区域附件的频道必须明确选择策略；频道没有配置时等价于 `INHERIT`：

- `INHERIT`：不修改祖先结果
- `ADD`：加入本区音轨并保留祖先
- `REPLACE`：清空祖先后只使用本区音轨
- `BLOCK`：清空该频道，表达明确静音

父链始终按 Global→父→子应用，child 即使 `order` 更低也能覆盖或恢复祖先结果；
`order` 只比较互不相关的重叠分支，较高 order 的 `BLOCK` 会压制较低分支。

例如外层森林在 `ambience` 使用 `ADD` 播放鸟鸣，内部小屋使用 `BLOCK`，玩家进入小屋后该频道立即静音；物理 RegionEnter/Leave 仍正常触发。

## API 与 WorldGuard

插件通过 Bukkit ServicesManager 发布 `RookieRegionsApi`，提供固定快照的 `RegionQuery`、Flag Registry、Native Provider、模块绑定解析器和可选 WorldGuard Provider。区域文件中的 Music/Commands profile 默认使用自身 Native 几何，也可用 `/rr module bind` 映射到同世界的 WorldGuard region ID；Music 和物理 Enter/Leave Commands 随 provider 几何命中，原生保护仍只查询 RookieRegions。

WorldGuard 适配器只读缓存 Cuboid/Polygon、priority、parent、owners 和 members，不接管 RookieRegions 写事务。`snapshot()` 只读取最后一次完整捕获，`refresh()` 仅在主线程显式执行；捕获失败继续保留 last-good 快照。WorldGuard 的逻辑 parent 若不满足 RookieRegions 的真包含约束，会确定性展开到 `__global__` 并给出诊断。

所有保护监听器都在事件发生时同步查询不可变快照；Music 的周期扫描不参与方块、交互、PvP 或 entry 判定。

第三方插件可通过 Bukkit `ServicesManager` 获取 `RookieRegionsApi`、
`RegionMutationApi` 与仅在 `onLoad()` 开放的 `RookieRegionsBootstrap`。API 提供语义版本、
capability 检测、自定义 typed Flag/只读 RegionProvider 注册、包含 bypass 的保护判定、
原子异步写入及 Bukkit 区域事件。
