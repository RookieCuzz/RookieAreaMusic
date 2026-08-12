# RookieAreaMusic

RookieAreaMusic 是面向 Paper 1.21.4 的区域音乐插件，Bukkit 加载名为 `RookieAreaMusic`，管理命令为 `/am`。

它使用 CT ROI 式逐层多边形编辑器描述三维区域，并提供 BGM 独占播放、Ambience 多层叠加、Stinger 入场触发、进入/离开区域时的控制台命令动作和固定坐标音源。CraftEngine 是可选依赖，可负责 OGG、Sound Event、资源包合并与下发；PlaceholderAPI 也是可选依赖，可为命令动作展开其他插件提供的占位符。

## 运行环境

- Minecraft / Paper：1.21.4
- 服务端 Java：21
- CraftEngine：可选；已在 26.7.4 上完成资源包声音与多频道实服测试
- PlaceholderAPI：可选；未安装时仍可使用 RookieAreaMusic 内置的玩家、区域与坐标占位符
- 构建：Maven 3.9+、JDK 21

插件保持 Java 8 字节码输出，但 Paper 1.21.4 服务端本身必须使用 Java 21。

## 主要功能

- 按整数 Y 层勾画不同 X/Z Polygon，形成 `sliced_polygon` 三维区域。
- 玩家私有粒子预览、ActionBar 状态、顶点撤销和切片复制。
- `bgm` 独占、`ambience` 最多三层叠加、`stinger` 只在进入时触发。
- 支持自定义频道、优先级、同级排序、随机/顺序播放、循环和覆盖策略。
- `enter_once` 区域可在入场及配对离场时按顺序执行控制台命令，支持玩家、区域、世界与坐标占位符。
- 固定坐标音源支持独立位置、时长、间隔、音量、音高和启停。
- 配置重载使用运行时原子快照；单区域编辑只原子替换自己的配置。
- Chunk 空间索引、玩家位置缓存和异步区域判断。
- CraftEngine 资源包成功加载后，自动刷新该玩家的区域声音。

## 安装

1. 把 `target/RookieAreaMusic-1.1.0.jar` 放入 Paper 1.21.4 的 `plugins/`。
2. 如需自定义 OGG，同时安装 CraftEngine，并部署 `examples/craftengine/rookie_music/` 模板。
3. 如需在区域动作命令中使用其他插件提供的 `%...%` 占位符，可选安装 PlaceholderAPI 及对应扩展。
4. 启动服务器，确认控制台出现 `RookieAreaMusic` 启用信息。
5. 修改配置后执行 `/am reload`；修改 CraftEngine 资源时先执行 `/ce reload all`。

RookieAreaMusic 的运行目录是 `plugins/RookieAreaMusic/`：

```text
plugins/RookieAreaMusic/
├─ config.yml
└─ worlds/
   └─ <世界名>/
      ├─ regions/<区域ID>/
      │  ├─ area.json
      │  └─ music.json
      └─ sources/<音源ID>.json
```

## 五分钟创建区域

管理员在目标世界执行即可；执行命令时的站位不会决定切片高度：

```text
/am area create spawn
```

进入编辑模式后会获得五件临时工具：

- 烈焰棒：新切片第一次右键的方块 `blockY` 决定该层 Y；后续右键只添加 X/Z 顶点；左键撤销；潜行左键清空。
- 黄绿色染料：保存当前层并进入下一切片；普通使用时复制上一层轮廓，潜行使用时创建空白轮廓。潜行后若选择已有 Y，则会从空白重画并在保存时替换该层。新切片仍由第一次右键方块决定 Y。
- 时钟：保存并返回上一已保存切片。
- 绿宝石：完成、写盘、重建索引。
- 屏障：五秒内二次使用，取消并回滚本次修改。

编辑已有区域时会先载入最低切片并保留其原 Y；使用“下一切片”后，再右键目标高度的方块即可载入该 Y 的已有切片，或在新的 Y 创建切片。

注意：这里记录的是被点击方块自身的原始 `blockY`，不是方块顶面或玩家脚部 Y。最后一张切片的有效高度仅为 `[blockY, blockY + 1)`；例如点击 Y=64 的地面方块后，站在其顶面的玩家脚部通常是 Y=65，并不属于这张最终切片。需要覆盖站立位置时，可放置临时标记方块选择脚部所在层，或再添加一张更高切片，并用 `/am area show` 核对完整范围。

完成并保存后，可在区域所在世界查看已生效的完整轮廓：

```text
/am area show world spawn
```

粒子轮廓仅对执行命令的玩家可见，可用来核对最低层、最高层和每张切片的范围。再次执行同一命令即可关闭；切换世界、退出服务器或重载插件也会结束预览。

随后给区域添加音乐：

```text
/am music add world spawn daytime rookie_music:bgm.overworld_day 180
```

这里的 `rookie_music:bgm.overworld_day` 是 Sound Event，不是 OGG 文件路径；`180` 是实际音频时长（秒）。

## 默认频道

| 频道 | 模式 | 默认层数 | 触发方式 | 典型用途 |
|---|---|---:|---|---|
| `bgm` | 独占 | 1 | 持续 | 主背景音乐、Boss 战音乐 |
| `ambience` | 叠加 | 3 | 持续 | 风、鸟、水、机器环境层 |
| `stinger` | 叠加 | 2 | 进入一次 | 地标提示、Boss 入场、发现音效 |

区域可使用相同几何互相重叠。例如森林范围内可分别建立风、鸟、水三个 `ambience` 区域，三条声音会同时播放。第四条候选会被层数限制压制；任一现有层离开后会自动补位。

## 进入与离开区域时执行命令

`trigger: enter_once` 的频道区域可在自己的 `area.json` 中配置 `enterCommands` 和 `exitCommands`：

```json
{
  "channel": "stinger",
  "enterCommands": [
    "title {player} title {\"text\":\"发现 Boss 区域\",\"color\":\"dark_red\"}",
    "tag {player} add discovered_boss_gate"
  ],
  "exitCommands": [
    "tag {player} remove discovered_boss_gate",
    "effect clear {player} minecraft:glowing"
  ]
}
```

命令由服务端控制台在 Bukkit 主线程按数组顺序执行。每个字符串只是一条命令，不会按分号、`&&` 或管道再次拆分；推荐不写开头的 `/`，但允许至多一个前导 `/`。只有实际进入、且在 `maxLayers` 限制下真正入选层位的区域才触发入场命令；至少一条入场命令由 Bukkit 成功派发后，插件才登记一次 activation token，并在走出、传送离开、切换世界或重载后该区域不再命中时消费 token，执行一次进入时冻结的配对离场命令。`exitCommands` 不是独立的离开监听器，不能在没有成功入场动作时单独触发。被层数限制压制的区域不会执行入场或离场命令。玩家掉线与插件停服默认不执行离场命令。

声音循环、重载后仍命中同 UUID 区域以及 CraftEngine 资源包声音补播都不会重复执行入场命令；命令失败也不会自动重试。若重载删除区域或改变形状，使玩家不再命中，则会执行已登记 token 的离场清理。`music.json` 可以为空，因此也能建立只执行命令、不播放声音的区域。位置类占位符使用该组命令在主线程开始派发时的玩家当前位置；`{area_world}` 始终表示区域所属世界。

`{player}`、`{player_uuid}` 等内置占位符无需安装其他插件。安装 PlaceholderAPI 后，还可以使用对应扩展提供的其他 `%...%` 占位符；未解析的占位符会使该条命令被安全跳过。完整字段、占位符表、长度限制与安全边界见[配置格式参考](CONFIG_FORMAT.zh-CN.md#入场与离场命令动作)。

可在 `config.yml` 中把 `actions.commands.enabled` 改为 `false`，统一关闭所有区域入场/离场命令。关闭期间的触发不会排队，重新开启后需要离开并再次进入区域。

## 管理命令

```text
/am area create <区域ID>
/am area edit <世界> <区域ID>
/am area show <世界> <区域ID>
/am area editor <finish|cancel>
/am area list [页码]
/am area del <世界> <区域ID>
/am music add <世界> <区域ID> <音乐ID> <声音键> <秒数>
/am music del <世界> <区域ID> <音乐ID>
/am music list <世界> <区域ID> [页码]
/am reload
/am help
```

需要权限 `rookieareamusic.admin`，默认仅 OP 拥有。

## 文档与示例

- [配置实操指南](CONFIG_GUIDE.zh-CN.md)：从 OGG、CraftEngine 到多频道区域的完整步骤。
- [配置格式参考](CONFIG_FORMAT.zh-CN.md)：所有 JSON 字段、频道规则、空间索引与线程模型。
- [CraftEngine 声音模板](examples/craftengine/rookie_music/configuration/sounds.yml)：可直接复制后替换 OGG。
- [区域与固定音源示例](examples/worlds/)：BGM、三层 Ambience、Stinger 和坐标音源。

## 构建与测试

```powershell
mvn clean test
mvn package
```

产物：`target/RookieAreaMusic-1.1.0.jar`。

当前测试覆盖切片几何与资源上限、编辑会话、空间索引、异步 revision、频道选择、BGM 覆盖、Ambience 层数与补位、Stinger、入场/离场命令、占位符、共享声音引用、循环策略和固定音源生命周期。

## 许可证

项目按 [MIT License](LICENSE) 开源；完整版权与许可声明同时打包在插件 JAR 的 `META-INF/LICENSE` 中。

## 重要限制

- 只读取新的分目录配置；旧根目录集中式 JSON 和木棍两点长方体流程已移除。
- 区域形状只接受 `sliced_polygon`，Y 必须为整数层；每层至少三个点，面积必须非零且不能自相交。
- 单区域最多 512 张切片、每张 512 个顶点、合计 32768 个顶点。
- 最高切片只占其整数 Y 所在的一格；如需覆盖更高位置，请继续建立更高切片。
- OGG 时长不会自动探测，`duration` 应与音频实际长度一致。
- `enterCommands`、`exitCommands` 只允许用于 `enter_once` 频道；两个数组分别最多 16 条、单条最多 1024 个字符、各自合计最多 8192 个字符。
- 固定坐标音源要获得明确方向感，应使用单声道 OGG；立体声素材通常不会产生可靠的空间方向。
