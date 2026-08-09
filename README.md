# RookieAreaMusic

RookieAreaMusic 是面向 Paper 1.21.4 的区域音乐插件，Bukkit 加载名为 `RookieAreaMusic`，管理命令为 `/am`。

它使用 CT ROI 式逐层多边形编辑器描述三维区域，并提供 BGM 独占播放、Ambience 多层叠加、Stinger 入场触发和固定坐标音源。CraftEngine 是可选依赖，可负责 OGG、Sound Event、资源包合并与下发。

## 运行环境

- Minecraft / Paper：1.21.4
- 服务端 Java：21
- CraftEngine：可选；已在 26.7.4 上完成资源包声音与多频道实服测试
- 构建：Maven 3.9+、JDK 21

插件保持 Java 8 字节码输出，但 Paper 1.21.4 服务端本身必须使用 Java 21。

## 主要功能

- 按整数 Y 层勾画不同 X/Z Polygon，形成 `sliced_polygon` 三维区域。
- 玩家私有粒子预览、ActionBar 状态、顶点撤销和切片复制。
- `bgm` 独占、`ambience` 最多三层叠加、`stinger` 只在进入时触发。
- 支持自定义频道、优先级、同级排序、随机/顺序播放、循环和覆盖策略。
- 固定坐标音源支持独立位置、时长、间隔、音量、音高和启停。
- 配置重载使用运行时原子快照；单区域编辑只原子替换自己的配置。
- Chunk 空间索引、玩家位置缓存和异步区域判断。
- CraftEngine 资源包成功加载后，自动刷新该玩家的区域声音。

## 安装

1. 把 `target/RookieAreaMusic-1.0.0.jar` 放入 Paper 1.21.4 的 `plugins/`。
2. 如需自定义 OGG，同时安装 CraftEngine，并部署 `examples/craftengine/rookie_music/` 模板。
3. 启动服务器，确认控制台出现 `RookieAreaMusic` 启用信息。
4. 修改配置后执行 `/am reload`；修改 CraftEngine 资源时先执行 `/ce reload all`。

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

从旧名称升级时，如果新目录不存在或为空，插件会自动把 `plugins/AreaMusic/` 迁移为 `plugins/RookieAreaMusic/`。如果两个目录都有内容，则拒绝自动覆盖并在控制台给出警告。

## 五分钟创建区域

管理员站在区域最低层执行：

```text
/am area create spawn
```

进入编辑模式后会获得五件临时工具：

- 烈焰棒：右键添加顶点；左键撤销；潜行左键清空当前切片。
- 黄绿色染料：保存当前层并进入下一层；潜行使用时不复制上一层轮廓。
- 时钟：保存并返回上一已保存切片。
- 绿宝石：完成、写盘、重建索引。
- 屏障：五秒内二次使用，取消并回滚本次修改。

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

## 管理命令

```text
/am area create <区域ID>
/am area edit <世界> <区域ID>
/am area editor <finish|cancel>
/am area list [页码]
/am area del <世界> <区域ID>
/am music add <世界> <区域ID> <音乐ID> <声音键> <秒数>
/am music del <世界> <区域ID> <音乐ID>
/am music list <世界> <区域ID> [页码]
/am reload
/am help
```

需要权限 `area-music.admin`，默认仅 OP 拥有。

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

产物：`target/RookieAreaMusic-1.0.0.jar`。

当前测试覆盖切片几何与资源上限、编辑会话、空间索引、异步 revision、频道选择、BGM 覆盖、Ambience 层数与补位、Stinger、共享声音引用、循环策略和固定音源生命周期。

## 来源与许可证

RookieAreaMusic 基于 Niocho 原作 AreaMusic（2021）开发，保留原作者版权声明；RookieCuzz 负责当前项目的持续维护与大幅改造。项目按 [MIT License](LICENSE) 开源。

## 重要限制

- 只读取新的分目录配置；旧根目录集中式 JSON 和木棍两点长方体流程已移除。
- 区域形状只接受 `sliced_polygon`，Y 必须为整数层；每层至少三个点，面积必须非零且不能自相交。
- 单区域最多 512 张切片、每张 512 个顶点、合计 32768 个顶点。
- 最高切片只占其整数 Y 所在的一格；如需覆盖更高位置，请继续建立更高切片。
- OGG 时长不会自动探测，`duration` 应与音频实际长度一致。
- 固定坐标音源要获得明确方向感，应使用单声道 OGG；立体声素材通常不会产生可靠的空间方向。
