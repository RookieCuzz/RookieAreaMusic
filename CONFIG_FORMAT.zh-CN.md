# RookieRegions 2.0 配置格式

本文是 `schemaVersion: 1` 的规范参考。实际部署步骤与 `/rr` 命令示例见
[CONFIG_GUIDE.zh-CN.md](CONFIG_GUIDE.zh-CN.md)。2.0 不读取 RookieAreaMusic 1.x
的 `area.json`、`music.json`、`sources/` 或 `/am` 命令。

## 数据目录与身份

```text
plugins/RookieRegions/
├─ config.yml
├─ worlds/
   └─ <world-uuid>/
      └─ regions/
         └─ <region-id>.json
└─ .trash/
   └─ <world-uuid>/
      └─ <region-id>.<timestamp>.<uuid>.json
```

- 世界目录名必须是 Bukkit 世界 UUID，例如
  `00000000-0000-0000-0000-000000000001`，不是世界名称。
- 文件名必须与文档中的规范化 `id` 完全一致。例如 ID `spawn` 只能保存为
  `spawn.json`。
- 文档中的 `world.uuid` 必须匹配目录 UUID。`world.key` 是可读元数据，例如
  `minecraft:overworld`；世界已加载时以内存中的当前 Namespaced Key 为准，未加载世界
  的同目录文档则必须彼此一致。
- 区域 ID 必须已经是规范化小写，只允许 `[a-z0-9._-]+`；加载器不会代为转换。
  世界 UUID 是身份，Namespaced Key 只用于可读诊断。
- 删除区域不会直接清空文件，而是移动到根目录 `.trash/<world-uuid>/` 下的唯一文件名。

插件会完整 staging `worlds/` 下的全部规范 UUID 目录，包括当前未加载的世界；任一
文档失败都会拒绝整次启动或 reload。已加载世界以及至少含一个区域文档的未加载世界
若没有 `__global__.json`，会自动合成空的 `__global__`：Global Shape、无 parent、
空 owners/members、priority 为 `-2147483648`。

游戏内可执行 `/rr region create global` 将这个合成根区域直接持久化；该操作不需要
几何选点，因为 Global Shape 始终覆盖所属世界的全部有限坐标。

## 严格 JSON 契约

区域文档使用 UTF-8 严格 JSON。以下情况都会拒绝整个 reload：

- 缺少任意必填字段；
- 任意层出现未知字段、未知模块、未知 Shape type 或未知 Flag；
- 重复对象 key，包括嵌套对象中的重复 key；
- 注释、尾逗号、单引号、根值后的额外内容；
- `NaN`、`Infinity`、数值溢出或字段类型不符；
- 文档世界、ID、文件名或目录互不匹配；
- parent 缺失、跨世界、成环、形状不包含 child，或 global 约束不成立。

错误会报告文件路径和 RFC 6901 JSON Pointer，例如
`spawn.json#/modules/music/channels/bgm/policy`。输入对象字段顺序不影响读取；插件写回
时使用固定字段顺序，并排序 domain、Flag 与频道 key，便于版本控制。

reload 会先在后台完整 staging 所有已加载世界，再一次性验证父子图。只有全部成功
才发布新 `RegionSnapshot`；任一文件失败时不会发布部分结果，旧快照继续运行。

保存使用同目录临时文件、强制落盘和原子移动。文件系统不支持 `ATOMIC_MOVE` 时保存
失败，不会降级成可能被读到半份内容的普通覆盖。

## 完整区域文档

```json
{
  "schemaVersion": 1,
  "id": "forest",
  "world": {
    "uuid": "00000000-0000-0000-0000-000000000001",
    "key": "minecraft:overworld"
  },
  "parent": "__global__",
  "priority": 5,
  "shape": {
    "type": "polygon",
    "minY": 50.0,
    "maxY": 100.0,
    "vertices": [
      { "x": 0.0, "z": 0.0 },
      { "x": 100.0, "z": 0.0 },
      { "x": 100.0, "z": 100.0 },
      { "x": 0.0, "z": 100.0 }
    ]
  },
  "owners": {
    "players": ["11111111-1111-1111-1111-111111111111"],
    "groups": ["builders"]
  },
  "members": {
    "players": [],
    "groups": ["visitors"]
  },
  "flags": {
    "core.allow-player-regions": "allow",
    "build": "deny",
    "pvp": "allow"
  },
  "modules": {
    "music": {
      "binding": null,
      "channels": {
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
      }
    },
    "commands": {
      "binding": null,
      "enter": ["title {player} actionbar {\"text\":\"进入森林\"}"],
      "leave": ["title {player} actionbar {\"text\":\"离开森林\"}"]
    }
  }
}
```

根对象只允许以下十个字段，且全部必填：

| 字段 | JSON 类型 | 约束 |
| --- | --- | --- |
| `schemaVersion` | integer | 必须为 `1` |
| `id` | string | 与文件名一致，规范化后匹配 `[a-z0-9._-]+` |
| `world` | object | 只含必填 `uuid`、`key` |
| `parent` | string 或 null | 普通区域必填同世界 parent ID；global 必须为 null |
| `priority` | 32-bit integer | 重叠分支的 Flag 决策优先级 |
| `shape` | object | 下述四种 Shape 之一 |
| `owners` | object | 必填 `players`、`groups` 数组 |
| `members` | object | 必填 `players`、`groups` 数组 |
| `flags` | object | Flag 名到强类型 JSON 值；可为空对象 |
| `modules` | object | 必须且只能包含 `music`、`commands` |

## Shape

所有坐标必须是有限数值。X/Z 边界包含在区域中，Y 使用半开区间：包含 `minY`，不包含
`maxY`。这使上下相邻的两个区域不会同时命中同一个 Y 平面。

### Cuboid

```json
{
  "type": "cuboid",
  "min": { "x": 0.0, "y": 64.0, "z": 0.0 },
  "max": { "x": 16.0, "y": 80.0, "z": 16.0 }
}
```

`min` 与 `max` 都必须且只能包含 `x`、`y`、`z`。三个轴都必须有正长度。木斧选择
方块时，编辑器把较大方块坐标加一后写成 `max`，所以选择同一个方块也会生成
1×1×1 的有效 Bounds。

### Polygon Prism

```json
{
  "type": "polygon",
  "minY": 60.0,
  "maxY": 90.0,
  "vertices": [
    { "x": 0.0, "z": 0.0 },
    { "x": 20.0, "z": 0.0 },
    { "x": 10.0, "z": 20.0 }
  ]
}
```

Polygon 至少三个有效 X/Z 顶点，必须是简单、非自交、非零面积轮廓。
`maxY > minY`。首尾闭合点可以输入，但规范输出会省略重复的最后一个点。

### Sliced Polygon

```json
{
  "type": "sliced",
  "minY": 60.0,
  "maxY": 100.0,
  "slices": [
    {
      "y": 60.0,
      "vertices": [
        { "x": 0.0, "z": 0.0 },
        { "x": 30.0, "z": 0.0 },
        { "x": 30.0, "z": 30.0 },
        { "x": 0.0, "z": 30.0 }
      ]
    },
    {
      "y": 80.0,
      "vertices": [
        { "x": 5.0, "z": 5.0 },
        { "x": 25.0, "z": 5.0 },
        { "x": 25.0, "z": 25.0 },
        { "x": 5.0, "z": 25.0 }
      ]
    }
  ]
}
```

`minY` 与 `maxY` 都是必填的显式边界，且 `maxY > minY`。切片必须按 `y` 严格递增，
首个切片的 `y` 必须与 `minY` 完全相等，因此 `[minY,maxY)` 内不会出现未定义空层。
每个轮廓从自己的 `y` 生效，持续到下一切片；最后一层持续到显式 `maxY`。`maxY`
必须高于最后切片。每层可使用完全不同的顶点数和轮廓，但每层都必须是有效 Polygon。

硬上限为 512 个切片、每层 512 个顶点、总计 32768 个顶点。

### Global

```json
{ "type": "global" }
```

只有 ID `__global__` 可使用。该文档必须 `parent: null`。普通区域不能使用 Global
Shape；global owners 会作为所有有限子区的祖先 owner 继承，members 仍只作用于
global 本身。

## Parent、覆盖与 priority

- 普通区域必须有 parent，且 parent 必须存在于同一世界。
- child 对 parent 的几何关系必须为 `INSIDE`。child 可以贴着 parent 的墙面、地面
  或顶面，但不能与 parent 完全相等，也不能越界。
- parent 图不能成环。删除有 child 的 parent 会被管理事务拒绝。
- 互不相关的区域可以只接触边界；`TOUCHING` 不算正体积覆盖。
- `/rr` 创建/编辑时，部分覆盖、相等、包含或新的 peer 正体积覆盖会按权限拒绝或要求
  一次性确认。直接手改 JSON 属于管理员操作；reload 会验证 parent 图，但不会替你
  执行命令层的覆盖授权流程。
- Flag 解析遇到多个互不相关分支时只保留最高 `priority` 的贡献；同 priority 的内置
  State Flag 冲突由 `deny` 胜出。

## Owners 与 members

```json
"owners": {
  "players": ["11111111-1111-1111-1111-111111111111"],
  "groups": ["admins"]
}
```

- `players` 只接受标准 UUID 字符串。
- `groups` 是区域内使用的组 ID，读取时去除两端空白并转为小写。玩家拥有有效权限节点
  `rookieregions.group.<组ID>` 时才会被运行时 `Subject` 识别为该组；可通过权限插件
  授予这个动态节点。
- Owner 身份沿 parent 链向 child 生效；member 只检查当前叶区域的 members。
- Owners/members 不会覆盖显式 `deny`，只参与未显式设置时的默认保护决策。

## 强类型 Flag

`flags` 不是任意字符串 Map。每个 key 必须存在于运行时 Flag Registry，值必须通过该
Flag 的 codec。当前内置 Flag 全部是 State，JSON 值必须是字符串 `allow` 或 `deny`；
`null` 不是 unset，要取消显式值应删除该属性或使用 `/rr ... unset`。

| Flag | 未显式设置时的行为 |
| --- | --- |
| `build` | 荒野、owner/member 允许；其他玩家拒绝 |
| `block-break` | 未设置时回退到 `build` |
| `block-place` | 未设置时回退到 `build` |
| `use` | 荒野、owner/member 允许；其他玩家拒绝 |
| `container` | 未设置时回退到 `use`；use 也未设置时，荒野、owner/member 允许，其他玩家拒绝 |
| `pvp` | 允许 |
| `entry` | 允许 |
| `explosion` | 允许 |
| `core.allow-player-regions` | 拒绝；只认目标 parent 上本地显式的 `allow` |

除 `core.allow-player-regions` 为 `LOCAL_ONLY` 外，内置保护 Flag 会从最近的有限祖先
继承，然后尝试 global 显式值，最后使用默认值。对应的
`rookieregions.bypass.*` 权限可绕过保护决定。

修改 `core.allow-player-regions` 必须拥有 `rookieregions.admin`；只有
`rookieregions.region.flag` 不足以修改它。其他内置保护 Flag 使用
`rookieregions.region.flag` 作为修改权限。

## Music 模块

固定结构如下；即使没有音乐，也必须写 `"channels": {}`：

```json
"music": {
  "binding": null,
  "channels": {}
}
```

`binding` 必填。`null` 表示使用保存该 profile 的 RookieRegions 区域自身几何；也可以
显式映射到同世界的只读 provider 区域：

```json
"binding": {
  "provider": "worldguard",
  "region": "forest"
}
```

`provider` 与 `region` 必须已经是去除首尾空白并转为小写后的规范形式。相同世界内，
同一模块的两个 profile 不得指向同一个 `provider + region`；完整 staging 和每次候选
快照提交都会拒绝这种歧义。外部 target 不存在时不会退回 Native 几何。

频道名必须与 `config.yml` 的 `music.channels` key 完全一致。每个频道策略必须且只能
包含以下字段：

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `policy` | string | `inherit`、`add`、`replace`、`block`，大小写不敏感 |
| `order` | 32-bit integer | 同频道的确定性策略/层排序 |
| `random` | boolean | 播放时从 tracks 随机选一首；false 使用第一首 |
| `loop` | boolean | duration 到期后是否再次播放 |
| `volume` | number | `0.0` 至 `1.0` |
| `pitch` | number | 大于 `0.0` 且不超过 `2.0` |
| `overwrite` | boolean | 同一播放层重播时是否先停止当前 Sound Event |
| `tracks` | array | 见下文；INHERIT/BLOCK 必须为空 |

四种策略的含义：

- `INHERIT`：显式不操作，保留此前从 parent 或其他适用区域累积的结果；tracks 必须
  为空。频道没有配置时也等价于 `INHERIT`。
- `ADD`：保留已有层，再加入本区域层；至少一首 track。
- `REPLACE`：清空本父链的祖先层以及 order 更低的无关分支层，再加入本区域层；至少
  一首 track。
- `BLOCK`：执行与 REPLACE 相同的清理但不加入音轨，并建立面向无关分支的 order
  阻断下限；order 更低的无关 ADD/REPLACE/BLOCK 会被压制。tracks 必须为空。

每条父链总是按 Global→父→child 应用，祖先与后代之间不比较 order；因此 child 即使
order 更低，ADD/REPLACE/BLOCK 仍会在祖先之后生效。只有互不相关的重叠区域按较低
order 到较高 order 应用，较高 order 的 REPLACE/BLOCK 可确定性覆盖较低分支。最终层
按 order、区域深度降序排序；`EXCLUSIVE` 频道只播放第一层，`LAYERED` 最多播放
`maxLayers` 层。

Track 必须且只能包含：

```json
{
  "id": "forest_wind",
  "sound": "rookie_music:ambience.forest_wind",
  "duration": 120
}
```

`id` 在同一频道策略内唯一，`sound` 是 Bukkit/CraftEngine Sound Event，`duration`
是正整数秒。2.0 没有 1.x 的独立固定坐标 `sources/*.json` 模块。

## Commands 模块

```json
"commands": {
  "binding": null,
  "enter": ["say {player} entered {region}"],
  "leave": ["say {player} left {region}"]
}
```

两个数组都必填，可以为空。字符串不能为空；一个前导 `/` 会在读取时移除。命令根据
玩家的物理区域成员变化以服务端控制台身份执行：进入时 parent 先于 child，离开时
child 先于 parent。同一区域内持续移动不会重复执行。

当前展开五个内置占位符：

- `{player}`：玩家名称；
- `{uuid}`：玩家 UUID；
- `{region}`：实际命中的 provider region ID；
- `{provider}`：`rookieregions` 或 `worldguard`；
- `{profile}`：保存命令 profile 的完整 Native key，例如 `minecraft:overworld/forest`。

2.0 不提供逐条编辑 Commands 内容的命令；请严格手工修改完整区域 JSON 后执行
`/rr reload`。模块几何绑定可用 `/rr module bind|unbind|info` 管理。不要在文档中使用
1.x 的 PlaceholderAPI 占位符或 activation-token 语义。

## Provider 绑定

Native 是默认 provider。以下命令把已有 profile 映射到只读 provider 几何，或恢复
Native 自身几何：

```text
/rr module bind <music|commands> <profile-region> <provider> <provider-region>
/rr module unbind <music|commands> <profile-region>
/rr module info <music|commands> <profile-region>
```

2.0 内建 `rookieregions` 与可选 `worldguard`。绑定只改变 Music/Commands 的几何来源，
绝不让 WorldGuard 接管 RookieRegions 原生保护或写事务。WorldGuard 捕获和外部 ID
映射作为同一个不可变 view 发布；失败时继续使用 last-good view。非几何包含的
WorldGuard parent 会展开到 Global，并在 provider diagnostics 中说明原因。

## config.yml

`config.yml` 也是 `schemaVersion: 1`。当前运行时读取以下设置：

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

- `confirmationSeconds` 与 `scanPeriodTicks` 必须为正数。
- `music.channels` 至少一个；`EXCLUSIVE` 强制 `maxLayers: 1`，`LAYERED` 的
  `maxLayers` 必须为正数。
- `playerCreation.enabled: false` 阻止非管理员使用创建命令。
- `notifyDeniedActions` 控制保护拒绝提示。
- 严格区域 JSON 是不可关闭的持久化契约。
