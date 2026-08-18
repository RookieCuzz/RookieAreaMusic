# RookieRegions 2.0 配置与操作指南

本文面向实际服主，介绍怎样创建区域、设置保护、配置音乐并安全重载。字段级规范见
[CONFIG_FORMAT.zh-CN.md](CONFIG_FORMAT.zh-CN.md)，可直接复制的区域文件见
[examples/worlds](examples/worlds/)。

## 升级前先确认

2.0 是破坏性升级，不会读取 RookieAreaMusic 1.x 的数据。请先备份旧目录，再按新格式
重建区域：

- 插件名与数据目录变为 `RookieRegions`；
- 主命令变为 `/rr`，不再提供 `/am`；
- 每个区域只有一个 `schemaVersion: 1` JSON 文件；
- 世界目录使用 UUID，不使用世界名称；
- `area.json`、`music.json` 和 `sources/*.json` 都不是 2.0 输入；
- 固定坐标音源模块已移除，CraftEngine 仍可提供区域音乐所引用的 Sound Event。

运行环境为 Paper 1.21.4+ 与 Java 21。首次启动后，主要目录如下：

```text
plugins/RookieRegions/
├─ config.yml
└─ worlds/
   └─ <world-uuid>/
      └─ regions/
         └─ <region-id>.json
```

## 快速创建第一个区域

先执行一种创建命令：

```text
/rr region create spawn cuboid
/rr region create market polygon
/rr region create tower sliced
/rr region create global
```

区域 ID 会规范化为小写，只能包含小写字母、数字、点、下划线和连字符。创建 session
后使用木斧选点：

- `cuboid`：主手攻击选择第一个方块，使用键选择第二个方块。方块选择会转成半开
  Bounds，因此单独选择一个方块也能得到 1×1×1 区域。
- `polygon`：两种点击都会按顺序加入一个 X/Z 顶点。初始 `minY` 是创建时玩家脚下的
  方块 Y，初始顶部是 `minY + 1`；用 `/rr region editor min-y <y>` 与
  `/rr region editor max-y <y>` 显式设置上下界。
- `sliced`：两种点击都会向当前切片加入 X/Z 顶点。显式 `minY` 与初始切片 Y 都是
  玩家脚下的方块 Y，且保存时二者必须相等；
  用 `/rr region editor slice <y>` 切换或建立切片，用
  `/rr region editor min-y <y>` 移动首切片与显式下界，并用
  `/rr region editor max-y <y>` 设置最后一层的显式顶部。

Polygon 与每个 sliced 切片都至少需要三个顶点，轮廓不能自交或为零面积；sliced 的
切片 Y 必须严格递增，首个切片 Y 必须等于显式 `minY`，`maxY` 必须高于最后一层。
常用编辑命令：

```text
/rr region editor undo
/rr region editor clear
/rr region editor slice 80
/rr region editor max-y 100
/rr region editor finish
/rr region editor cancel
```

`finish` 会验证形状、父子关系、权限和覆盖关系。`cancel` 是显式放弃：它会结束当前
session 并释放区域编辑锁。等待确认或保存失败不会偷偷结束 session，也不会释放锁，
可以修正选点后再次 `finish`。

编辑已有区域使用：

```text
/rr region edit spawn
```

同一玩家同一时间只有一个编辑 session，同一区域也只允许一个玩家持有编辑锁。

## 覆盖、接触与确认

RookieRegions 精确区分六种几何关系：`DISJOINT`、`TOUCHING`、`INSIDE`、
`CONTAINS`、`EQUAL`、`OVERLAP`。

- 只共享墙面、边、点或上下 Y 边界属于 `TOUCHING`，不算正体积冲突。
- 普通 child 必须严格位于 parent 内；可以贴 parent 边界，但不能与 parent 相等。
- 部分穿插、完全相等、包住已有区域或同级正体积重叠都需要放置策略作出明确决定，
  不会静默保存。
- 普通玩家在已有区域内创建 child 时，目标 parent 必须在自身 `flags` 中本地显式设置
  `"core.allow-player-regions": "allow"`。继承来的 allow 不算。
- 拥有 `rookieregions.region.overlap` 的管理员可以从聊天确认项中选择允许覆盖或设置
  parent。确认项实际会携带一次性 token 重新执行
  `/rr region editor finish <token>`；没有单独的 `/rr confirm` 命令。

token 绑定玩家、session、候选形状、放置方案和快照版本，超时、重放、区域变化或
重新选点后都会失效。这是为了避免“确认的内容”和最终落盘内容不一致。

直接修改 JSON 属于管理员操作：reload 仍会严格验证父子图，但不会补做游戏内创建
流程的权限和覆盖确认。

## 层级、身份与管理命令

每个世界都有 `__global__` 根区域。普通区域必须拥有同世界 parent；若磁盘上没有
`__global__.json`，插件会合成一个空 global。创建者会自动成为新区域 owner，荒野中
创建的区域默认挂到 global。

管理员执行 `/rr region create global` 时不需要木斧或选点：插件会将当前世界唯一的
`__global__` 直接原子保存，它天然覆盖该世界全部有限坐标。该命令也接受完整写法
`/rr region create __global__ global`。Global 可设置 Flag、owner、member、Music 和
Commands，但不能删除或设置 parent；它不参与普通区域重叠确认和 priority 竞争。

```text
/rr region info <id>
/rr region list
/rr region priority <id> <integer>
/rr region parent <id> <parent|global>
/rr region delete <id>
```

删除仍有 child 的 parent 会被拒绝，应先移动或删除 child。`priority` 只用于互不相关
的重叠分支作确定性 Flag 决策，不代替 parent 层级。

Owner 与 member 支持标准玩家 UUID、在线玩家名和 `group:<组名>`：

```text
/rr region owner forest add 11111111-1111-1111-1111-111111111111
/rr region owner forest add group:builders
/rr region owner forest remove group:builders
/rr region member forest add SomeOnlinePlayer
/rr region member forest add group:visitors
/rr region member forest remove group:visitors
```

组不会自动读取 Vault 等权限提供者的主组。请通过权限插件给玩家授予
`rookieregions.group.<组名>`，例如 `rookieregions.group.builders`；运行时据此建立
`Subject.groups`。

Owner 身份沿 parent 链向 child 生效；member 只属于当前区域。身份只参与未显式设置时
的默认保护判断，不会推翻显式 `deny`。

## 设置强类型 Flag

当前内置 Flag 都是 State，值只能是 `allow` 或 `deny`：

```text
/rr region flag forest build deny
/rr region flag forest block-break allow
/rr region flag forest block-place allow
/rr region flag forest use deny
/rr region flag forest container deny
/rr region flag arena pvp allow
/rr region flag lobby entry deny
/rr region flag spawn explosion deny
/rr region flag forest core.allow-player-regions allow
/rr region flag forest use unset
```

`unset` 删除当前区域的显式值。除 `core.allow-player-regions` 只读取本地值外，保护 Flag
会从最近的有限祖先继承，再读取 global，最后使用默认值：

| Flag | 未显式设置时 |
| --- | --- |
| `build` | 荒野、owner/member 允许；其他玩家拒绝 |
| `block-break` | 回退到 `build` |
| `block-place` | 回退到 `build` |
| `use` | 荒野、owner/member 允许；其他玩家拒绝 |
| `container` | 未设置时回退到 `use`；use 也未设置时，荒野、owner/member 允许，其他玩家拒绝 |
| `pvp` | 允许 |
| `entry` | 允许 |
| `explosion` | 允许 |
| `core.allow-player-regions` | 拒绝；只认目标 parent 的本地显式值 |

多个无亲缘的区域同时命中时，先选最高 `priority` 的贡献；同 priority 的 State 冲突
由 `deny` 胜出。对应 `rookieregions.bypass.*` 权限可以绕过保护决定。

`core.allow-player-regions` 是管理员专用 Flag：修改它必须拥有
`rookieregions.admin`，只有 `rookieregions.region.flag` 不够。

## 配置音乐频道

每个区域文档的 `modules.music` 都必须包含 `"binding": null`（默认使用该 Native
区域）或一个 `{ "provider": "...", "region": "..." }` 对象。绑定管理命令见下文。

`config.yml` 先声明允许运行的频道：

```yaml
schemaVersion: 1

editor:
  confirmationSeconds: 30

playerCreation:
  enabled: true

protection:
  notifyDeniedActions: true

music:
  scanPeriodTicks: 20
  channels:
    bgm:
      mode: EXCLUSIVE
      maxLayers: 1
    ambience:
      mode: LAYERED
      maxLayers: 3
    stinger:
      mode: LAYERED
      maxLayers: 2
```

区域 JSON 或 `/rr music` 使用的频道名必须存在于这里。`EXCLUSIVE` 只播放一个层，
`LAYERED` 最多播放 `maxLayers` 个层。

四种策略都可用命令设置：

```text
/rr music forest ambience inherit
/rr music forest_hut ambience block
/rr music forest ambience add forest_wind rookie_music:ambience.forest_wind 120 10
/rr music spawn bgm replace spawn_day rookie_music:bgm.overworld_day 180 0
```

完整语法为：

```text
/rr music <region> <channel> <inherit|block>
/rr music <region> <channel> <add|replace> <track-id> <sound> <duration-seconds> [order]
```

命令形式的 ADD/REPLACE 写入一首 track，并沿用该频道已有的 random、loop、volume、
pitch 与 overwrite 设置；需要多首曲目或调整这些播放字段时，应严格编辑区域单文件
JSON 后 `/rr reload`。

| 策略 | 效果 |
| --- | --- |
| `INHERIT` | 不改变此前累积结果，tracks 必须为空；频道缺省配置也等价于此策略 |
| `ADD` | 保留已有层，并加入本区域层；至少一首 track |
| `REPLACE` | 清空祖先层及低 order 无关分支，再加入本区域层；至少一首 track |
| `BLOCK` | 清空祖先层及低 order 无关分支，并对无关分支建立 order 阻断下限；tracks 必须为空 |

每条父链严格按 Global→parent→child 应用，child 即使 order 更低也能覆盖或恢复祖先
结果。order 只比较互不相关的重叠区域，它们按较低到较高应用；较高 order 的 REPLACE
或 BLOCK 因而能确定性覆盖较低无关分支。`duration` 是正整数秒，必须接近 OGG 实际
长度；Minecraft/CraftEngine 不会把音频长度回传给插件。

一个完整频道对象示例：

```json
"ambience": {
  "policy": "add",
  "order": 10,
  "random": false,
  "loop": true,
  "volume": 0.5,
  "pitch": 1.0,
  "overwrite": true,
  "tracks": [
    {
      "id": "forest_wind",
      "sound": "rookie_music:ambience.forest_wind",
      "duration": 120
    }
  ]
}
```

## 使用 CraftEngine 提供声音

本仓库不附带 CraftEngine 模板或 OGG。请按所用 CraftEngine 版本的文档注册并部署
Sound Event 与资源包。区域文件的 `sound` 填 Sound Event，例如
`rookie_music:ambience.forest_wind`，不是 OGG 文件路径；长 BGM/环境音可在资源包声音
定义中使用 `stream: true`。

RookieRegions 2.0 不调度固定坐标音源。若需要瀑布、篝火等定位音效，应交给其他声音
系统处理，不能继续放置 1.x 的 `worlds/<name>/sources/*.json`。

## Commands 模块

Commands 内容目前只能严格编辑区域 JSON，没有逐条 Commands 编辑子命令：

```json
"commands": {
  "binding": null,
  "enter": [
    "say {player} entered {region}",
    "tag {player} add entered_forest"
  ],
  "leave": [
    "say {player} left {region}",
    "tag {player} remove entered_forest"
  ]
}
```

命令以控制台身份在物理成员关系变化时执行；进入时 parent 先于 child，离开时 child
先于 parent。每个字符串是一条命令，一个前导 `/` 会被移除。当前只支持：

- `{player}`：玩家名称；
- `{uuid}`：玩家 UUID；
- `{region}`：实际命中的 provider region ID；
- `{provider}`：`rookieregions` 或 `worldguard`；
- `{profile}`：保存该 profile 的完整 Native region key。

不要使用 1.x 的 activation token、位置占位符或 PlaceholderAPI 展开语义。

## 把模块绑定到 WorldGuard 区域

Music 与 Commands profile 默认使用保存它的 RookieRegions 区域自身几何。管理员可把
已有 profile 映射到同世界的 WorldGuard region，而不会改变任何原生保护规则：

```text
/rr module bind music forest_profile worldguard forest
/rr module bind commands lobby_profile worldguard spawn
/rr module info music forest_profile
/rr module unbind music forest_profile
```

`unbind` 恢复 Native 自身几何。绑定 target 必须在 provider 当前缓存 view 中存在；同一
模块、世界、provider 和 target 只能对应一个 profile。WorldGuard 缺失或 target 消失时
不会回退到 Native 区域，模块暂停生效并写出一次诊断。WorldGuard 刷新失败时保留最后
一次完整快照；它始终是只读几何来源，不参与 RookieRegions 的保护写入。

## 手工部署区域 JSON

示例目录使用占位 UUID。正式部署时先查到目标世界的真实 UUID，再把目录名和每个文档
的 `world.uuid` 一起替换；`world.key` 也必须匹配已加载世界。

```text
plugins/RookieRegions/worlds/<真实世界UUID>/regions/<region-id>.json
```

每个文档的字段、模块与嵌套字段都采用白名单，不能省略空的 owners、members、flags、
music 或 commands。JSON 不允许注释、尾逗号、重复 key、未知字段、NaN 或 Infinity。
修改完成后执行：

```text
/rr reload
```

reload 会完整 staging 所有已加载世界；任何一个文件失败时都保留旧快照，不会发布半套
数据。错误会带文件路径和 JSON Pointer。插件自身保存使用强制落盘与原子移动，不支持
原子移动的文件系统会明确保存失败。

## 完整命令速查

```text
/rr region create <id> <cuboid|polygon|sliced>
/rr region create global
/rr region edit <id>
/rr region delete <id>
/rr region info <id>
/rr region list
/rr region priority <id> <integer>
/rr region parent <id> <parent|global>
/rr region owner <id> <add|remove> <uuid|online-player|group:name>
/rr region member <id> <add|remove> <uuid|online-player|group:name>
/rr region flag <id> <flag> <value|unset>
/rr region editor finish [token]
/rr region editor cancel
/rr region editor undo
/rr region editor clear
/rr region editor slice <y>
/rr region editor min-y <y>
/rr region editor max-y <y>
/rr music <region> <channel> <inherit|block>
/rr music <region> <channel> <add|replace> <track-id> <sound> <duration-seconds> [order]
/rr module bind <music|commands> <profile-region> <provider> <provider-region>
/rr module unbind <music|commands> <profile-region>
/rr module info <music|commands> <profile-region>
/rr reload
/rr help
```

## 权限速查

| 权限 | 用途 |
| --- | --- |
| `rookieregions.admin` | 全部管理与 bypass 权限；默认 OP |
| `rookieregions.region.create` | 创建区域 |
| `rookieregions.region.edit.own` | 编辑自己拥有的区域 |
| `rookieregions.region.edit.any` | 编辑任意区域 |
| `rookieregions.region.delete` | 删除区域 |
| `rookieregions.region.view` | 查看与列出区域 |
| `rookieregions.region.overlap` | 使用管理员覆盖确认选项 |
| `rookieregions.region.flag` | 修改普通 Flag；不含管理员专用的 `core.allow-player-regions` |
| `rookieregions.group.<组名>` | 让玩家在区域查询中属于对应的 owners/members 组 |
| `rookieregions.module.music` | 修改 Music 附件 |
| `rookieregions.module.commands` | 修改 Commands 附件及其 provider 绑定 |
| `rookieregions.reload` | 重载配置与区域快照 |
| `rookieregions.bypass.build` | 绕过 build、block-break、block-place |
| `rookieregions.bypass.block-break` | 只绕过 block-break |
| `rookieregions.bypass.block-place` | 只绕过 block-place（含 multi-place） |
| `rookieregions.bypass.use` | 绕过 use |
| `rookieregions.bypass.container` | 绕过 container |
| `rookieregions.bypass.pvp` | 绕过 PvP |
| `rookieregions.bypass.entry` | 绕过 entry |
| `rookieregions.bypass.explosion` | 绕过 explosion |

## 常见问题

### reload 报世界不匹配

检查目录是否为世界 UUID，而不是 `world`、`world_nether` 等名称；再检查文档里的
`world.uuid` 和 `world.key` 是否与当前已加载世界一致。

### 区域看似相邻却提示覆盖

X/Z 边界包含，Y 为 `[minY, maxY)` 半开。单纯共享边界是 `TOUCHING`，不冲突；若有
任意正体积交集就不是单纯接触。重点检查 cuboid 的 max 坐标和 sliced 每层生效区间。

### 玩家不能在 parent 内创建 child

必须在目标 parent 自身显式设置：

```text
/rr region flag <parent> core.allow-player-regions allow
```

祖先或 global 上继承来的值不满足此条件；之后仍要通过一次性 parent 确认。

### 有区域命中但听不到音乐

- 客户端资源包尚未加载；
- `sound` 写成了 OGG 路径，而不是 Sound Event；
- 频道未在 `config.yml` 的 `music.channels` 中声明；
- 无关重叠分支中更高 order 的 `BLOCK` / `REPLACE` 清除了该层；
- `EXCLUSIVE` 或 `maxLayers` 把该层排除；
- track 的 `duration`、音量或 CraftEngine 资源路径配置不正确。

### 严格区域 JSON 能关闭吗

不能。严格区域 JSON 是 2.0 的持久化契约，当前配置中不存在 `storage.strict` 或其他
关闭验证的开关。
