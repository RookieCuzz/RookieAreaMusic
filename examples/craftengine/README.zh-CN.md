# CraftEngine 示例包

把 `rookie_music/` 整个目录复制到：

```text
plugins/CraftEngine/resources/rookie_music/
```

然后按 `sounds.yml` 中的 `name` 放入自己的 OGG：

```text
rookie_music/resourcepack/assets/rookie_music/sounds/
├─ music/
├─ ambience/
├─ stinger/
└─ source/
```

模板不附带 OGG。文件不存在时 CraftEngine 会报告缺失资源；请替换名称或补齐文件后执行 `/ce reload all`。

