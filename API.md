# RookieRegions 2.0 API

RookieRegions 在 Bukkit `ServicesManager` 中发布三个服务：

- `RookieRegionsBootstrap`：仅用于依赖插件的 `onLoad()` 注册 Flag 与 RegionProvider；
- `RookieRegionsApi`：不可变快照、查询、保护决策、Provider 和模块绑定；
- `RegionMutationApi`：异步、权限检查、原子保存的区域写入口。

## Maven 依赖

构建会同时生成：

```text
target/RookieRegions-2.0.0-SNAPSHOT.jar
target/RookieRegions-2.0.0-SNAPSHOT-api.jar
```

第三方插件只编译依赖 API classifier，禁止 shade：

```xml
<dependency>
    <groupId>io.github.rookiecuzz</groupId>
    <artifactId>RookieRegions</artifactId>
    <version>2.0.0-SNAPSHOT</version>
    <classifier>api</classifier>
    <scope>provided</scope>
</dependency>
```

主插件 JAR 与 API classifier 会由同一次 `deploy` 一起发布。当前 Maven settings 已使用
`nexus-releases` / `nexus-snapshots` server ID；部署时只需提供实际地址：

```bash
mvn deploy \
  -Drookieregions.nexus.releases=https://你的地址/repository/maven-releases/ \
  -Drookieregions.nexus.snapshots=https://你的地址/repository/maven-snapshots/
```

仓库 URL 不包含在现有项目或可见父 POM 中，因此这里不写入猜测地址；认证信息继续仅存于
用户 Maven settings。

运行时 `plugin.yml` 必须声明：

```yaml
depend: [RookieRegions]
```

API 采用语义版本。调用功能前可以检测能力：

```java
RookieRegionsApi api = Bukkit.getServicesManager()
        .load(RookieRegionsApi.class);

if(api == null || !api.version().isCompatibleWith(new ApiVersion(2, 0, 0))) {
    throw new IllegalStateException("RookieRegions API 2.x is required");
}
if(!api.supports(ApiCapability.PROTECTION_DECISIONS)) {
    throw new IllegalStateException("Protection query API is unavailable");
}
```

## onLoad 注册

自定义 Flag 必须在依赖插件的 `onLoad()` 注册。RookieRegions 在进入 `onEnable()`
和读取区域 JSON 前冻结注册表；迟到、重复或未知定义都会被拒绝。

```java
public static final StateFlag FLIGHT = new StateFlag("example.flight");

@Override
public void onLoad() {
    RookieRegionsBootstrap bootstrap = Bukkit.getServicesManager()
            .load(RookieRegionsBootstrap.class);
    if(bootstrap == null) {
        throw new IllegalStateException("RookieRegions bootstrap unavailable");
    }
    bootstrap.registerFlag(this, FLIGHT);
}
```

自定义 `RegionProvider` 同样在 `onLoad()` 注册：

```java
bootstrap.registerProvider(this, myReadOnlyProvider);
```

Provider ID 必须规范化且唯一；`rookieregions` 和 `worldguard` 是保留 ID。
Provider 的 `snapshot()`/`view()` 必须返回不可变、内部一致的缓存，不能在查询中访问
Bukkit 非线程安全状态。

## 固定快照查询

一次逻辑操作只获取一次 Query：

```java
RegionQuery query = api.query();
Location location = player.getLocation();
ApplicableRegionSet set = query.at(
        BukkitWorlds.id(location.getWorld()),
        location.getX(), location.getY(), location.getZ()
);
```

`RegionQuery` 与 `ProtectionQuery` 固定到创建时的 Snapshot revision，可以安全地跨多个
解析步骤使用。需要最新状态时重新调用 `api.query()` 或 `api.protection()`。

带权限 bypass 的最终保护判定：

```java
ProtectionDecision decision = api.protection().decideBuild(
        BukkitWorlds.id(location.getWorld()),
        location.getX(), location.getY(), location.getZ(),
        BukkitSubjects.from(player),
        BuildAction.BREAK
);
```

## 原子写入

Create：

```java
RegionSnapshot snapshot = api.snapshot();
RegionSaveRequest request = RegionSaveRequests.create(snapshot, candidate)
        .sessionId("my-plugin:" + player.getUniqueId())
        .build();

RegionMutationApi writes = Bukkit.getServicesManager()
        .load(RegionMutationApi.class);
writes.attemptSave(request, BukkitSubjects.from(player))
        .thenAccept(this::handleSaveResult);
```

Edit 会自动生成目标指纹：

```java
RegionSaveRequest request = RegionSaveRequests.edit(
        snapshot, targetKey, changedRegion
).build();
```

若结果为 `CONFIRMATION_REQUIRED`，展示服务端给出的 token；玩家选择后，用相同候选重新
构建 request 并调用 `.confirmationToken(token)`。不要自己构造或缓存关系计划。

Delete：

```java
writes.delete(regionKey, BukkitSubjects.from(player))
        .thenAccept(result -> {
            if(result.status() == RegionDeleteStatus.DELETED) {
                // 已落盘并发布新 Snapshot
            }
        });
```

所有写入均为异步。`CompletionStage` 回调不保证在 Paper 主线程；需要调用 Bukkit API 时
必须自行切回 Scheduler。

## Bukkit 事件

可监听：

- `RegionCreateEvent` / `RegionUpdateEvent` / `RegionDeleteEvent`
- `RegionEnterAttemptEvent`（可取消）
- `RegionEnterEvent` / `RegionLeaveEvent`
- `EffectiveFlagChangeEvent`
- `SnapshotPublishedEvent`

事件由 RookieRegions 在 Paper 主线程触发。完整示例见
[`examples/api-plugin`](examples/api-plugin)。

## 稳定边界

兼容性承诺覆盖 API classifier 中的 `api` 契约、查询模型、Flag 类型、Region/Shape 模型、
Provider 契约、事件和 mutation request/outcome。`persistence`、`editor`、监听器、命令实现及
名称包含 Bukkit Service/Resolver 实现的类型不属于稳定扩展点。
