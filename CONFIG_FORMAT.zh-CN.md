# RookieAreaMusic 分区配置指南

如果要从 OGG 和 CraftEngine 开始搭建整套配置，请先看 [CONFIG_GUIDE.zh-CN.md](CONFIG_GUIDE.zh-CN.md)。本文作为字段、播放规则和运行模型的完整参考。

每个区域使用一对独立 JSON。修改单个区域时，只需把该目录交给 LLM，不必加载其他世界、区域或曲目。

```text
plugins/RookieAreaMusic/
├─ config.yml
└─ worlds/
   └─ <世界名>/
      ├─ regions/
      │  └─ <区域 ID>/
      │     ├─ area.json
      │     └─ music.json
      └─ sources/
         └─ <音源 ID>.json
```

世界名与区域 ID 来自目录名；内部 UUID 由插件稳定生成，不写进 JSON。

## area.json

```json
{
  "channel": "bgm",
  "order": 0,
  "priority": "NORMAL",
  "random": false,
  "loop": true,
  "enabled": true,
  "overwrite": true,
  "volume": 1.0,
  "pitch": 1.0,
  "enterCommands": [],
  "exitCommands": [],
  "shape": {
    "type": "sliced_polygon",
    "slices": [
      {
        "y": 60.0,
        "polygon": [
          { "x": 120.0, "z": 120.0 },
          { "x": 180.0, "z": 120.0 },
          { "x": 180.0, "z": 180.0 },
          { "x": 120.0, "z": 180.0 }
        ]
      },
      {
        "y": 80.0,
        "polygon": [
          { "x": 100.0, "z": 130.0 },
          { "x": 140.0, "z": 100.0 },
          { "x": 190.0, "z": 120.0 },
          { "x": 200.0, "z": 170.0 },
          { "x": 160.0, "z": 200.0 },
          { "x": 110.0, "z": 180.0 }
        ]
      },
      {
        "y": 100.0,
        "polygon": [
          { "x": 150.0, "z": 120.0 },
          { "x": 190.0, "z": 180.0 },
          { "x": 110.0, "z": 180.0 }
        ]
      }
    ]
  }
}
```

`channel` 决定混音频段；`order` 用于同优先级排序，值越大越优先。旧文件缺少这两个字段时自动采用 `bgm` 和 `0`。

候选区域固定按 `priority` 降序、`order` 降序、区域 ID 升序排列。

### CT 切片生长

切片按 `y` 从低到高排列。每个 Polygon 原样向上生长，直到相邻的更高切片接管：

```text
Y=60–79   使用 Y=60 的四边形
Y=80–99   使用 Y=80 的六边形
Y=100     使用 Y=100 的三角形
```

各切片可以有不同顶点数，不要求顶点一一对应。`y` 必须是 32 位整数方块层；Polygon 至少需要 3 个点且不得自相交。最高切片默认占一格高度。单区域最多 512 张切片、每张最多 512 个顶点、全部切片合计最多 32768 个顶点；这些上限会在昂贵的自相交检查前验证，以保护服务器主线程。

区域几何只接受 `shape.type = sliced_polygon`。旧 `min` / `max`、`minPoint` / `maxPoint` 长方体格式不再读取，也不会自动迁移。

## 游戏内 CT ROI 编辑器

管理员在目标世界执行：

```text
/am area create <区域ID>
/am area edit <世界> <区域ID>
/am area show <世界> <区域ID>
```

进入编辑模式需要至少 5 个空背包格，会获得 5 件只在本次会话有效的工具：

- 烈焰棒「ROI 勾画笔」：新切片第一次右键使用该方块的 `blockY` 锁定切片 Y；空白切片同时添加顶点 #1，复制轮廓的切片只锁定 Y、不追加顶点。后续右键只读取方块中心 X/Z；左键撤销；潜行左键清空当前切片。
- 黄绿色染料「保存并选择下一切片」：进入 Y 待选择状态；普通使用时，点击新 Y 会复制当前轮廓，点击已有 Y 会载入该层原轮廓；潜行使用则从空轮廓开始，也可在已有 Y 重画该层。第一次右键可选择任意更高且有效的方块层。
- 时钟「上一层」：验证并暂存当前层，返回上一张已保存切片。
- 绿宝石「完成编辑」：原子写入该区域的 `area.json`，并立即重建空间索引。
- 屏障「取消编辑」：5 秒内再次使用后放弃本次所有修改。

工具可在玩家自己的 36 格背包与快捷栏之间移动，但不能丢弃、放入副手、盔甲格、工作台或外部容器。工具丢失时可使用 `/am area editor finish` 或 `/am area editor cancel`。同一区域同时只能被一名管理员编辑；编辑期间不能删除该区域或增删其音乐。退出、切换世界、死亡、重载或停服会取消会话并清理工具。

编辑已有区域时默认载入最低切片，且已有切片的 Y 不会因后续顶点点击而移动。要切换到更高的已有切片，先使用“下一切片”，再右键该切片 Y 上的方块。

编辑器保存的是被点击方块自身的原始 `blockY`，不会自动加减一。几何体的最后一张切片只覆盖 `[blockY, blockY + 1)`：若点击 Y=64 的地面方块，站在其顶面的玩家脚部通常位于 Y=65，正好落在排除上界。配置站立区域时应选择脚部实际所在层（可临时放置标记方块）或补充更高切片，并用 `/am area show` 检查粒子轮廓。

## music.json

```json
{
  "music": [
    {
      "id": "spawn_day",
      "sound": "rookie_music:bgm.overworld_day",
      "duration": 180
    }
  ]
}
```

`id` 只需在当前区域内唯一；`sound` 是资源包 Sound Event；`duration` 单位为秒。

## 播放频道

频道与区域动作命令总开关在 `config.yml` 中统一声明：

```yaml
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

- `exclusive`：同一玩家在该频道只保留一层，并强制 `maxLayers: 1`。当前区域仍有效时，只有 `overwrite: true` 且优先级严格更高的新区域才能抢占。
- `additive`：按排序同时播放前 `maxLayers` 个区域，忽略 `overwrite`；有空位时立即补入下一候选。
- `continuous`：区域持续有效；曲目到期后，只有 `loop: true` 才选择下一首。
- `enter_once`：只在从区域外进入区域内时触发并忽略 `loop`。因层数限制被压制后，必须离开再进入才会触发。

`actions.commands.enabled: false` 会关闭全部 `enterCommands` 与 `exitCommands`。关闭期间的动作不会排队或延迟补执行；重新开启后，玩家需要离开并重新进入区域才会建立新的配对动作。

可增加自定义频道。名称只能包含小写字母、数字、点、下划线和连字符。未知频道、非法模式、非法触发器或非法层数会使整次 `/am reload` 失败，旧运行配置继续生效。

相同玩家的相同 Sound Event 只实际播放一次。多个区域共同持有引用，最后一个引用离开后才执行 `stopSound`。

## 入场与离场命令动作

区域可通过 `area.json` 的可选 `enterCommands` 与 `exitCommands` 数组，在玩家进入及其配对离开时调用原版命令或服务端已注册的其他插件命令：

```json
{
  "channel": "stinger",
  "enterCommands": [
    "title {player} title {\"text\":\"发现 Boss 区域\",\"color\":\"dark_red\"}",
    "/tag {player} add discovered_{area_id}",
    "effect give {player} minecraft:glowing 5 0 true"
  ],
  "exitCommands": [
    "tag {player} remove discovered_{area_id}",
    "effect clear {player} minecraft:glowing"
  ]
}
```

### 触发语义

- 非空 `enterCommands` 或 `exitCommands` 只允许用于 `trigger: enter_once` 的频道；在 `continuous` 频道中配置任一数组都会使加载或 `/am reload` 失败。字段缺失或为 `null` 时按空数组处理。
- 只有区域由“未命中”变为“命中”，并且该区域在频道排序与 `maxLayers` 限制后实际入选层位时，才执行命令。默认 Stinger 同时最多触发两层；被压制的区域必须离开再进入，不会因后来出现空位而延迟补触发。
- 只有某次物理进入真正入选、且至少一条 `enterCommands` 在主线程获得 Bukkit 的成功派发结果，插件才登记 activation token。随后走出、传送离开、切换世界或重载后不再命中该区域时，会消费同一 token 并执行一次入场时冻结的 `exitCommands`。如果所有入场命令都失败或被跳过，就没有配对离场动作；`exitCommands` 不是独立的离开监听器。被压制的区域没有 token；玩家掉线或插件停服默认不执行离场命令。
- 命令与声音引用分别触发。两个区域使用同一 Sound Event 时，声音可能只播放一次，但两个入选区域各自的命令仍会执行。
- `music.json` 可以为空；这种 command-only 区域可执行入场与配对离场命令，不要求配置声音。
- 一直停留在区域内不会重复执行。曲目循环或续播、重载后仍命中同 UUID 区域、CraftEngine 资源包就绪后的声音补播均不会再次执行入场命令。若 `/am reload` 删除区域或改变形状使玩家不再命中，插件把它视为逻辑离开并执行已登记 token 的冻结离场动作；离开后重新进入才会建立新 token。

### 执行格式

命令以服务端控制台身份，在 Bukkit 主线程按数组顺序执行。一个字符串严格对应一次命令派发：RookieAreaMusic 不会按分号、`&&`、管道或引号再次拆分，也不会调用操作系统 Shell。推荐不写命令开头的 `/`；为了方便复制游戏内命令，允许至多一个前导 `/`，执行前会自动移除。

每条命令独立执行。某条命令抛出异常、返回失败或占位符无法解析时，插件记录原因、跳过或结束该条，然后继续执行后面的命令。失败命令不会自动重试，因为目标插件可能已经执行了部分副作用。只有至少一次 Bukkit 命令派发返回成功，才登记本次入场的 activation token；离场尝试会先消费 token，即使离场命令失败也不会反复重试。

### 内置占位符

内置占位符不依赖 PlaceholderAPI。花括号与百分号两种写法等价：

| 含义 | 花括号 | 百分号 |
|---|---|---|
| 玩家名 | `{player}`、`{player_name}` | `%player%`、`%player_name%` |
| 玩家 UUID | `{player_uuid}` | `%player_uuid%` |
| 当前世界 | `{world}` | `%world%` |
| 区域所属世界 | `{area_world}` | `%area_world%` |
| 区域 ID | `{area}`、`{area_id}` | `%area%`、`%area_id%` |
| 区域内部 UUID | `{area_uuid}` | `%area_uuid%` |
| 玩家精确坐标 | `{x}`、`{y}`、`{z}` | `%x%`、`%y%`、`%z%` |
| 玩家方块坐标 | `{block_x}`、`{block_y}`、`{block_z}` | `%block_x%`、`%block_y%`、`%block_z%` |

位置类占位符取该组命令在 Bukkit 主线程开始派发时的玩家当前位置。通常，入场动作得到区域内位置，离场动作得到走出或传送后的新位置；若玩家在异步判定与主线程派发之间又快速移动或连续传送，则以实际开始派发时的位置为准，并不保证是第一次越界的落点。`{area_world}` / `%area_world%` 和其他区域类占位符仍取 activation token 对应的区域信息。

安装 PlaceholderAPI 后，RookieAreaMusic 还会展开其他扩展提供的 `%...%`，例如 `%luckperms_primary_group%`。PlaceholderAPI 是可选依赖；未安装时所有内置占位符仍然工作。非内置占位符没有对应扩展、扩展报错或展开后仍保留 `%...%` 时，该条命令会被跳过，而不是把未解析文本发送给目标插件。

### 限制与安全边界

- `enterCommands` 与 `exitCommands` 分别最多配置 16 条命令；每条模板最多 1024 个字符，每个数组的全部模板各自合计最多 8192 个字符；占位符展开后的单条命令最多 4096 个字符。
- 命令在读取配置及占位符展开后都会检查长度与控制字符；空命令、NUL、CR/LF、两个前导 `/` 或超限内容会被拒绝或跳过。
- `area.json` 等配置文件等同于受信任的服主管理输入，因为命令拥有控制台权限。不要把写入这些文件或修改 `enterCommands`、`exitCommands` 的能力交给普通玩家。
- PlaceholderAPI 扩展可能返回玩家可控文本。Bukkit 命令之间没有通用参数转义规则，因此应优先把 `{player_uuid}` 或 `{player}` 放在目标命令明确要求玩家标识的位置，避免把自由文本占位符放到权限、命令名或其他敏感参数中。
- RookieAreaMusic 只保证“一条配置字符串只派发一次”并阻断换行等多命令载荷；目标插件如何解释空格、引号及参数，仍由该插件决定。

## 固定坐标音源

固定音源适合树上的鸟叫、瀑布、篝火、机器等有明确发声位置的环境音。每个音源使用一个独立文件：

```text
worlds/<世界名>/sources/<音源 ID>.json
```

示例 `tree_birds.json`：

```json
{
  "position": {
    "x": 128.5,
    "y": 72.5,
    "z": 144.5
  },
  "sound": "rookie_music:source.tree_birds",
  "duration": 6,
  "interval": 12,
  "volume": 1.0,
  "pitch": 1.0,
  "enabled": true
}
```

- `position`：声音实际发出的世界坐标，建议使用方块中心的 `.5` 坐标。
- `sound`：资源包 Sound Event。
- `duration`：一次声音的持续秒数，必须大于 0。
- `interval`：一次播放结束后的静默秒数，`0` 表示连续衔接。
- `volume`：原始音量，范围 `(0, 16]`；允许大于 1 来扩大原版可听距离。
- `pitch`：音高，范围 `(0, 2]`。
- `enabled`：是否启用；省略时默认为 `true`。

音量与方向由 Minecraft 固定位置声音原生处理，会随玩家移动连续变化，不会每秒重播来模拟音量。原版可听距离约为 `16 × max(1, volume)` 格；资源包中的声音衰减设置也会影响最终听感。建议鸟叫素材使用非循环音频，并通过 `duration + interval` 控制重复节奏。

多个固定音源即使使用相同 Sound Event，也会从各自坐标独立发声。由于 Bukkit 只能按声音键和类别停止客户端声音，正在发声的同键音源被删除、禁用或修改时，插件会先停止该键的 `AMBIENT` 声音，再立即补播当时仍在发声的有效音源；处于静默间隔的音源不会被提前播放。

## CraftEngine 自定义声音对接

RookieAreaMusic 与 CraftEngine 通过标准的 Minecraft Sound Event 键松耦合对接。CraftEngine 负责 OGG 文件、`sounds.json` 生成、资源包合并与下发；RookieAreaMusic 不读取 CraftEngine 的资源文件，也不配置或上传材质包，只在区域或固定音源需要播放时调用 Bukkit 声音 API。区域曲目使用跟随玩家实体的 `MUSIC` 声音类别，固定坐标音源使用世界坐标上的 `AMBIENT` 类别，因此玩家移动不会让长 BGM 远离听者，停止区域曲目也不会误停同键固定音源。

先在 CraftEngine 的包配置中声明声音。例如长音乐建议启用流式读取：

```yaml
sounds:
  rookie_music:bgm.overworld_day:
    sounds:
      - name: "rookie_music:music/overworld_day"
        stream: true
        attenuation_distance: 256
```

然后只把同一个事件键写入 RookieAreaMusic：

```json
{
  "music": [
    {
      "id": "spawn_day",
      "sound": "rookie_music:bgm.overworld_day",
      "duration": 180
    }
  ]
}
```

- `sound` 必须是声音事件键，不是 OGG 路径或文件名。建议始终使用完整的 `namespace:path`。
- RookieAreaMusic 仍需 `duration`，因为声音事件本身不包含 OGG 的实际时长；该值用于循环、换曲和引用生命周期。
- 固定坐标音源同样直接填写 CraftEngine 事件键，例如 `rookie_music:source.tree_birds`。可听距离与衰减主要由 Minecraft、RookieAreaMusic 的 `volume` 以及 CraftEngine 声音条目的 `attenuation_distance` 共同决定。
- 可直接复制 [CraftEngine 多声道声音模板](examples/craftengine/rookie_music/configuration/sounds.yml)。模板包含长 BGM、三层 Ambience、Stinger 和固定音源配置。
- RookieAreaMusic 不强制查询 CraftEngine 内部声音表。这样既允许使用 CraftEngine 生成的事件，也允许使用原版或其他资源包提供的事件，并避免绑定 CraftEngine 的内部版本。

`plugin.yml` 将 CraftEngine 声明为可选依赖。检测到 CraftEngine 时，玩家资源包报告 `SUCCESSFULLY_LOADED` 后，RookieAreaMusic 会等待 10 ticks 做防抖，再为该玩家串行执行“停止旧区域声音 → 重新判断区域 → 播放当前声音”。当前区域的 `enter_once` 声音可以补播，确保客户端不会因资源包尚未就绪而错过入场音；该维护性补播不会再次执行 `enterCommands` 或 `exitCommands`。固定坐标音源不强制重播，会在下一次正常周期播放。

推荐重载顺序：

1. OGG、CraftEngine 声音配置或资源包内容发生变化：先执行 `/ce reload all`，并按 CraftEngine 的流程重新生成或下发资源包。
2. RookieAreaMusic 的声音事件键、时长、区域或固定音源配置发生变化：再执行 `/am reload`。
3. 只替换同一事件键对应的 OGG、且 RookieAreaMusic 的时长不变时，不需要执行 `/am reload`。

## 查询与线程模型

插件加载或重载时会：

- 对切片高度排序并用二分搜索定位活动切片：`O(log S)`；
- 预计算 Polygon 边数据，点在多边形内判断为 `O(V)`；
- 按 Minecraft Chunk 建立空间索引，将世界全部 `R` 个区域缩小为附近 `K` 个候选；
- 按玩家 UUID、世界和精确坐标缓存上次结果。玩家未移动时复用；即使仍在同一方块内，只要坐标变化也会重新判断边界。
- 固定音源被删除、禁用或修改时主动停止旧的 `AMBIENT` 实例；多个同键音源受影响时立即补播仍有效的实例。世界暂未加载或一次播放失败时会在 1 秒后重试。

缓存未命中时，单次区域查询约为 `O(K × (log S + V))`。覆盖超过 4096 个 Chunk 的超大区域作为世界级候选处理，避免展开出大量索引项。

每 20 ticks（约 1 秒）扫描一次在线玩家，最多分成 4 个异步分片。主线程只抓取不可变的位置快照并调度固定音源；Chunk 查询、切片判断和区域播放决策在异步线程执行；最终 `playSound`、`stopSound`、占位符展开与控制台命令派发回到 Bukkit 主线程按玩家 FIFO 顺序执行。登录和传送会立即提交一次判断，同一玩家的任务保持串行；尚未开始的旧 revision 会被丢弃，已经开始并推进逻辑会话的任务则完整提交输出增量，后续 revision 再按 FIFO 修正，保证逻辑状态与客户端状态不会分叉。

运行时的曲目、频道、固定音源和空间索引通过一个原子快照发布，因此重载期间不会混用新旧配置。重载成功后会立即刷新固定音源并重新计算在线玩家，但仍处于同一区域的 `enter_once` 不会重复触发。

## 给 LLM 的最小编辑上下文

- 修改区域范围、播放策略或入场/离场命令：只提供该区域的 `area.json`。
- 新增、删除或修改音乐：只提供该区域的 `music.json`。
- 新建区域：复制两个文件到 `worlds/<世界名>/regions/<新区域 ID>/`。
- 新增或修改固定音源：只提供对应的 `worlds/<世界名>/sources/<音源 ID>.json`。
- 修改后执行 `/am reload`。任一配置无效时会拒绝整次重载并继续使用旧运行配置。

## 管理命令

```text
/am music add <世界> <区域> <音乐ID> <声音键> <秒数>
/am music del <世界> <区域> <音乐ID>
/am music list <世界> <区域> [页码]
/am area create <区域ID>
/am area edit <世界> <区域ID>
/am area show <世界> <区域ID>
/am area editor <finish|cancel>
/am area del <世界> <区域>
/am reload
```

旧版根目录下的集中式 `area.json` / `music.json` 和木棍两点长方体编辑流程已移除，不会自动迁移。
