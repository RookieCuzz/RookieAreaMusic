package io.github.rookiecuzz.rookieareamusic.editor;

import io.github.rookiecuzz.rookieareamusic.RookieAreaMusic;
import io.github.rookiecuzz.rookieareamusic.config.AreaDto;
import io.github.rookiecuzz.rookieareamusic.config.ConfigManager;
import io.github.rookiecuzz.rookieareamusic.config.Priority;
import io.github.rookiecuzz.rookieareamusic.config.RegionShapeConfig;
import io.github.rookiecuzz.rookieareamusic.geometry.RegionPreviewWireframe;
import io.github.rookiecuzz.rookieareamusic.geometry.SlicedPolygonVolume;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public final class RegionEditorService {
    private static final int TOOL_COUNT = 5;
    private static final int PARTICLE_BUDGET = 512;
    private static final Particle.DustOptions PREVIEW_BASE_DUST =
            new Particle.DustOptions(Color.LIME, 0.85f);
    private static final Particle.DustOptions PREVIEW_TRANSITION_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 165, 0), 0.85f);
    private static final Particle.DustOptions PREVIEW_VERTICAL_DUST =
            new Particle.DustOptions(Color.AQUA, 0.75f);
    private static final Particle.DustOptions PREVIEW_FINAL_TOP_DUST =
            new Particle.DustOptions(Color.RED, 0.95f);
    private static final Particle.DustOptions PREVIEW_FALLBACK_DUST =
            new Particle.DustOptions(Color.WHITE, 0.8f);

    private final RookieAreaMusic plugin;
    private final RegionEditorManager manager = new RegionEditorManager();
    private final RegionPreviewManager previewManager = new RegionPreviewManager();
    private final Map<UUID, AreaDto> originalAreas = new HashMap<>();
    private final Map<UUID, PreviewRenderState> previewRenderStates = new HashMap<>();
    private final NamespacedKey toolKey;
    private BukkitTask visualizationTask;

    public RegionEditorService(RookieAreaMusic plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "region_editor_tool");
    }

    public void start(){
        if(visualizationTask != null){
            return;
        }
        visualizationTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::renderVisualizations,
                10L,
                10L
        );
    }

    public void stop(){
        if(visualizationTask != null){
            visualizationTask.cancel();
            visualizationTask = null;
        }
        for(Map.Entry<UUID, RegionEditSession> entry : manager.snapshot()){
            Player player = Bukkit.getPlayer(entry.getKey());
            if(player != null){
                removeTools(player);
            }
        }
        originalAreas.clear();
        manager.clear();
        previewRenderStates.clear();
        previewManager.clear();
    }

    public boolean beginCreate(Player player, String areaId){
        if(player == null){
            return false;
        }
        if(!isValidAreaId(areaId)){
            player.sendMessage("§c[RookieAreaMusic] 区域 ID 只能包含英文字母、数字、点、下划线和连字符");
            return false;
        }
        String worldName = player.getWorld().getName();
        if(plugin.getConfigManager().findArea(worldName, areaId) != null){
            player.sendMessage("§c[RookieAreaMusic] 区域已经存在: " + worldName + "/" + areaId);
            return false;
        }
        return begin(
                player,
                new RegionEditSession(
                        RegionEditSession.Mode.CREATE,
                        worldName,
                        areaId,
                        player.getWorld().getMinHeight(),
                        player.getWorld().getMaxHeight() - 1,
                        null,
                        null
                ),
                null
        );
    }

    public boolean beginEdit(Player player, String worldName, String areaId){
        if(player == null){
            return false;
        }
        World world = Bukkit.getWorld(worldName);
        if(world == null){
            player.sendMessage("§c[RookieAreaMusic] 世界未加载: " + worldName);
            return false;
        }
        if(!player.getWorld().equals(world)){
            player.sendMessage("§c[RookieAreaMusic] 请先进入世界 " + worldName + " 再编辑该区域");
            return false;
        }
        AreaDto area = plugin.getConfigManager().findArea(worldName, areaId);
        if(area == null){
            player.sendMessage("§c[RookieAreaMusic] 未找到区域 " + worldName + "/" + areaId);
            return false;
        }
        try {
            RegionEditSession session = new RegionEditSession(
                    RegionEditSession.Mode.EDIT,
                    worldName,
                    areaId,
                    world.getMinHeight(),
                    world.getMaxHeight() - 1,
                    null,
                    area.getShape().getConfig()
            );
            return begin(player, session, copyArea(area));
        } catch (IllegalArgumentException e){
            player.sendMessage("§c[RookieAreaMusic] 无法进入编辑模式: " + e.getMessage());
            return false;
        }
    }

    public RegionEditSession getSession(UUID playerUuid){
        return manager.get(playerUuid);
    }

    public boolean isRegionLocked(String worldName, String areaId){
        return manager.isLocked(worldName, areaId);
    }

    /**
     * Toggles a saved region's complete wireframe for one player. The preview
     * deliberately does not acquire the editor lock because it never mutates
     * the published region.
     */
    public boolean toggleAreaPreview(Player player,
                                     String worldName,
                                     String areaId){
        if(player == null){
            return false;
        }

        UUID playerUuid = player.getUniqueId();
        RegionPreviewManager.Target current = previewManager.get(playerUuid);
        if(current != null
                && current.getWorldName().equals(worldName)
                && current.getAreaId().equals(areaId)){
            clearAreaPreview(playerUuid);
            player.sendMessage("§e[RookieAreaMusic] 已关闭区域轮廓预览: "
                    + current.getWorldName() + "/" + current.getAreaId());
            return true;
        }
        if(isBlank(worldName) || isBlank(areaId)){
            player.sendMessage("§c[RookieAreaMusic] 世界和区域 ID 不能为空");
            return false;
        }
        if(manager.get(playerUuid) != null){
            player.sendMessage("§c[RookieAreaMusic] 请先完成或取消当前区域编辑，再开启已保存区域预览");
            return false;
        }

        World world = Bukkit.getWorld(worldName);
        if(world == null){
            player.sendMessage("§c[RookieAreaMusic] 世界未加载: " + worldName);
            return false;
        }
        if(!player.getWorld().equals(world)){
            player.sendMessage("§c[RookieAreaMusic] 请先进入世界 " + worldName + " 再查看该区域");
            return false;
        }

        AreaDto area = plugin.getConfigManager().findArea(worldName, areaId);
        if(area == null || area.getShape() == null){
            player.sendMessage("§c[RookieAreaMusic] 未找到有效区域 " + worldName + "/" + areaId);
            return false;
        }

        try {
            RegionPreviewWireframe wireframe = RegionPreviewWireframe.from(
                    area.getShape().getConfig()
            );
            previewManager.toggle(playerUuid, worldName, areaId);
            previewRenderStates.put(
                    playerUuid,
                    new PreviewRenderState(area, area.getShape(), wireframe)
            );
            player.sendMessage("§a[RookieAreaMusic] 已开启区域轮廓预览: "
                    + worldName + "/" + areaId + "（仅你可见，再次执行同一命令关闭）");
            player.sendMessage("§7有效 Y: §f[" + formatCoordinate(wireframe.getMinY())
                    + ", " + formatCoordinate(wireframe.getMaxExclusiveY()) + ")"
                    + " §7| §a绿=切片底面 §6橙=层间顶界"
                    + " §b蓝=垂直范围 §c红=最终排除顶界");
            player.sendMessage("§7请靠近区域观察；玩家位置判定使用脚部坐标。");
            return true;
        } catch (IllegalArgumentException e){
            player.sendMessage("§c[RookieAreaMusic] 无法显示区域轮廓: " + e.getMessage());
            return false;
        }
    }

    public boolean clearAreaPreview(UUID playerUuid){
        if(playerUuid == null){
            return false;
        }
        previewRenderStates.remove(playerUuid);
        return previewManager.remove(playerUuid);
    }

    public RegionEditorTool identifyTool(ItemStack item){
        if(item == null || item.getType().isAir()){
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if(meta == null){
            return null;
        }
        return RegionEditorTool.fromId(meta.getPersistentDataContainer().get(
                toolKey,
                PersistentDataType.STRING
        ));
    }

    public boolean containsEditorTool(ItemStack item){
        if(item == null || item.getType().isAir()){
            return false;
        }
        if(identifyTool(item) != null){
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if(!(meta instanceof BundleMeta)){
            return false;
        }
        for(ItemStack bundledItem : ((BundleMeta) meta).getItems()){
            if(containsEditorTool(bundledItem)){
                return true;
            }
        }
        return false;
    }

    public void addPoint(Player player, Location blockLocation){
        RegionEditSession session = requireSession(player);
        if(session == null || blockLocation == null){
            return;
        }
        World blockWorld = blockLocation.getWorld();
        if(blockWorld == null
                || !session.getWorldName().equals(blockWorld.getName())){
            player.sendMessage("§c[RookieAreaMusic] 顶点必须位于编辑区域的世界");
            return;
        }
        try {
            int clickedY = blockLocation.getBlockY();
            Integer lockedBefore = session.getCurrentY();
            RegionEditSession.AddPointResult result = session.addPoint(
                    clickedY,
                    blockLocation.getBlockX() + 0.5,
                    blockLocation.getBlockZ() + 0.5
            );
            switch (result){
                case HEIGHT_LOCKED_AND_POINT_ADDED:
                    player.sendMessage("§a[RookieAreaMusic] 首个方块已将当前切片锁定为 Y="
                            + session.getCurrentY() + "，并添加顶点 #1");
                    break;
                case HEIGHT_LOCKED_AND_EXISTING_POLYGON_REPLACED:
                    player.sendMessage("§e[RookieAreaMusic] 已选择已有切片 Y="
                            + session.getCurrentY()
                            + " 并从空白轮廓重画；保存后将替换该层，当前已添加顶点 #1");
                    break;
                case HEIGHT_LOCKED_FOR_COPIED_POLYGON:
                    player.sendMessage("§a[RookieAreaMusic] 首个方块已将当前切片锁定为 Y="
                            + session.getCurrentY() + "；轮廓来自上一层，本次未追加顶点");
                    break;
                case HEIGHT_LOCKED_FOR_EXISTING_POLYGON:
                    player.sendMessage("§a[RookieAreaMusic] 首个方块已选择已有切片 Y="
                            + session.getCurrentY() + "；已载入原轮廓，本次未追加顶点");
                    break;
                case POINT_ADDED:
                default:
                    String message = "§a[RookieAreaMusic] 已添加顶点 #"
                            + session.getDraft().size();
                    if(lockedBefore != null && lockedBefore != clickedY){
                        message += " §7（本层 Y=" + lockedBefore
                                + " 已锁定，本次只读取 X/Z）";
                    }
                    player.sendMessage(message);
                    break;
            }
        } catch (IllegalStateException | IllegalArgumentException e){
            player.sendMessage("§c[RookieAreaMusic] 无法添加顶点: " + e.getMessage());
        }
    }

    public void undoPoint(Player player){
        RegionEditSession session = requireSession(player);
        if(session == null){
            return;
        }
        boolean hadHeight = !session.isAwaitingHeight();
        boolean undone = session.undoLastPoint();
        if(undone && hadHeight && session.isAwaitingHeight()){
            player.sendMessage("§e[RookieAreaMusic] 已撤销当前切片的 Y 选择，请重新右键方块");
        } else {
            player.sendMessage(undone
                    ? "§e[RookieAreaMusic] 已撤销最后一个顶点"
                    : "§c[RookieAreaMusic] 当前切片没有可撤销的顶点");
        }
    }

    public void clearPoints(Player player){
        RegionEditSession session = requireSession(player);
        if(session == null){
            return;
        }
        session.clearCurrentSlice();
        player.sendMessage(session.isAwaitingHeight()
                ? "§e[RookieAreaMusic] 已清空当前待编辑切片并解除 Y 锁定，请重新右键方块"
                : "§e[RookieAreaMusic] 已清空当前切片（已有切片 Y 保持不变）");
    }

    public void nextSlice(Player player, boolean blank){
        RegionEditSession session = requireSession(player);
        if(session == null){
            return;
        }
        try {
            session.saveAndNext(blank);
            player.sendMessage("§a[RookieAreaMusic] 已进入 Y 待选择的下一切片；"
                    + "请用勾画笔右键第一个方块决定 blockY"
                    + (blank ? "（空白轮廓）" : "（将复制上一层或载入已有轮廓）"));
        } catch (IllegalStateException e){
            player.sendMessage("§c[RookieAreaMusic] " + e.getMessage());
        }
    }

    public void previousSlice(Player player){
        RegionEditSession session = requireSession(player);
        if(session == null){
            return;
        }
        try {
            session.saveAndPrevious();
            player.sendMessage("§a[RookieAreaMusic] 已返回 Y=" + session.getCurrentY());
        } catch (IllegalStateException e){
            player.sendMessage("§c[RookieAreaMusic] " + e.getMessage());
        }
    }

    public boolean finish(Player player){
        RegionEditSession session = requireSession(player);
        if(session == null){
            return false;
        }
        try {
            RegionShapeConfig shapeConfig = session.finish();
            SlicedPolygonVolume volume = new SlicedPolygonVolume(shapeConfig);
            boolean create = session.getMode() == RegionEditSession.Mode.CREATE;
            AreaDto area = create
                    ? createDefaultArea(session, volume)
                    : updateExistingArea(player.getUniqueId(), session, volume);
            plugin.getConfigManager().upsertRegion(area, create);
            plugin.rebuildSpatialIndex();
            end(player, false);
            player.sendMessage("§a[RookieAreaMusic] 区域 " + session.getWorldName() + "/"
                    + session.getAreaId() + " 已保存，共 "
                    + shapeConfig.getSlices().size() + " 张切片");
            if(create){
                player.sendMessage("§a[RookieAreaMusic] 使用 /am music add "
                        + session.getWorldName() + " " + session.getAreaId()
                        + " <音乐ID> <声音键> <秒数> 添加音乐");
            }
            return true;
        } catch (IllegalStateException | IllegalArgumentException e){
            player.sendMessage("§c[RookieAreaMusic] 无法完成编辑: " + e.getMessage());
        } catch (IOException e){
            plugin.getLogger().log(Level.SEVERE, "切片区域保存失败", e);
            player.sendMessage("§c[RookieAreaMusic] 保存失败，编辑会话仍保留: " + deepestMessage(e));
        }
        return false;
    }

    public void requestCancel(Player player){
        RegionEditSession session = requireSession(player);
        if(session == null){
            return;
        }
        if(!session.confirmCancel(System.currentTimeMillis())){
            player.sendMessage("§c[RookieAreaMusic] 5 秒内再次使用取消工具以确认放弃修改");
            return;
        }
        end(player, true);
    }

    public void cancelImmediately(Player player, boolean notify){
        if(player == null || manager.get(player.getUniqueId()) == null){
            return;
        }
        end(player, notify);
    }

    private boolean begin(Player player,
                          RegionEditSession session,
                          AreaDto originalArea){
        if(manager.get(player.getUniqueId()) != null){
            player.sendMessage("§c[RookieAreaMusic] 你已经处于区域编辑模式");
            return false;
        }
        removeTools(player);
        if(countEmptyStorageSlots(player.getInventory()) < TOOL_COUNT){
            player.sendMessage("§c[RookieAreaMusic] 进入编辑模式需要至少 5 个空背包槽");
            return false;
        }
        try {
            manager.begin(player.getUniqueId(), session);
        } catch (IllegalStateException e){
            player.sendMessage("§c[RookieAreaMusic] " + e.getMessage());
            return false;
        }
        clearAreaPreview(player.getUniqueId());
        if(originalArea != null){
            originalAreas.put(player.getUniqueId(), originalArea);
        }
        giveTools(player);
        player.sendMessage("§a[RookieAreaMusic] 已进入 CT ROI 编辑模式: "
                + session.getWorldName() + "/" + session.getAreaId());
        if(session.isAwaitingHeight()){
            player.sendMessage("§7首张切片 Y 尚未确定；勾画笔右键的第一个方块将锁定其 blockY");
        } else {
            player.sendMessage("§7已载入已有切片 Y=" + session.getCurrentY()
                    + "；其 Y 保持不变，右键只修改轮廓 X/Z");
        }
        return true;
    }

    private void end(Player player, boolean notify){
        manager.end(player.getUniqueId());
        originalAreas.remove(player.getUniqueId());
        removeTools(player);
        if(notify){
            player.sendMessage("§e[RookieAreaMusic] 已取消区域编辑，未写入任何修改");
        }
    }

    private RegionEditSession requireSession(Player player){
        if(player == null){
            return null;
        }
        RegionEditSession session = manager.get(player.getUniqueId());
        if(session == null){
            player.sendMessage("§c[RookieAreaMusic] 你不在区域编辑模式中");
        }
        return session;
    }

    private void giveTools(Player player){
        for(RegionEditorTool tool : RegionEditorTool.values()){
            ItemStack item = new ItemStack(tool.getMaterial());
            ItemMeta meta = item.getItemMeta();
            if(meta == null){
                continue;
            }
            meta.setDisplayName(tool.getDisplayName());
            meta.setLore(toolLore(tool));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(
                    toolKey,
                    PersistentDataType.STRING,
                    tool.getId()
            );
            item.setItemMeta(meta);
            player.getInventory().addItem(item);
        }
    }

    public void removeTools(Player player){
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for(int index = 0; index < contents.length; index++){
            inventory.setItem(index, removeEditorTools(contents[index]));
        }
    }

    public ItemStack removeEditorTools(ItemStack item){
        if(item == null || item.getType().isAir()){
            return item;
        }
        if(identifyTool(item) != null){
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if(!(meta instanceof BundleMeta)){
            return item;
        }

        BundleMeta bundleMeta = (BundleMeta) meta;
        List<ItemStack> retainedItems = new ArrayList<>();
        for(ItemStack bundledItem : bundleMeta.getItems()){
            ItemStack retainedItem = removeEditorTools(bundledItem);
            if(retainedItem != null && !retainedItem.getType().isAir()){
                retainedItems.add(retainedItem);
            }
        }
        bundleMeta.setItems(retainedItems);
        item.setItemMeta(bundleMeta);
        return item;
    }

    private List<String> toolLore(RegionEditorTool tool){
        switch (tool){
            case POINT:
                return Arrays.asList(
                        "§7新切片首次右键：用方块 blockY 锁定高度",
                        "§7后续右键：添加 X/Z 顶点",
                        "§7左键：撤销；潜行左键：清空"
                );
            case NEXT:
                return Arrays.asList(
                        "§7保存并等待点击下一切片的 Y",
                        "§7普通使用：复制上一层或载入已有轮廓",
                        "§7潜行使用：从空白轮廓开始重画"
                );
            case PREVIOUS:
                return Arrays.asList(
                        "§7已有当前 Y：保存轮廓并返回上一切片",
                        "§7Y 尚未选择：丢弃待选层并返回"
                );
            case FINISH:
                return Collections.singletonList("§7验证、保存全部切片并退出");
            case CANCEL:
                return Collections.singletonList("§7五秒内连续使用两次以放弃修改");
            default:
                return Collections.emptyList();
        }
    }

    private int countEmptyStorageSlots(PlayerInventory inventory){
        int result = 0;
        for(ItemStack item : inventory.getStorageContents()){
            if(item == null || item.getType().isAir()){
                result++;
            }
        }
        return result;
    }

    private AreaDto createDefaultArea(RegionEditSession session,
                                      SlicedPolygonVolume volume){
        return AreaDto.builder()
                .world(session.getWorldName())
                .uuid(ConfigManager.createRegionUuid(session.getWorldName(), session.getAreaId()))
                .areaId(session.getAreaId())
                .musicId(new CopyOnWriteArrayList<>())
                .channel("bgm")
                .order(0)
                .priority(Priority.NORMAL)
                .random(false)
                .loop(true)
                .enabled(true)
                .overWrite(true)
                .volume(1.0f)
                .pitch(1.0f)
                .enterCommands(new CopyOnWriteArrayList<>())
                .exitCommands(new CopyOnWriteArrayList<>())
                .shape(volume)
                .minPoint(volume.getMinPoint())
                .maxPoint(volume.getMaxPoint())
                .build();
    }

    private AreaDto updateExistingArea(UUID playerUuid,
                                       RegionEditSession session,
                                       SlicedPolygonVolume volume){
        AreaDto original = originalAreas.get(playerUuid);
        if(original == null){
            throw new IllegalStateException("原区域快照已丢失，请取消后重新进入编辑模式");
        }
        AreaDto result = copyArea(original);
        result.setShape(volume);
        result.setMinPoint(volume.getMinPoint());
        result.setMaxPoint(volume.getMaxPoint());
        result.setWorld(session.getWorldName());
        result.setAreaId(session.getAreaId());
        return result;
    }

    private AreaDto copyArea(AreaDto source){
        return AreaDto.builder()
                .world(source.getWorld())
                .uuid(source.getUuid())
                .areaId(source.getAreaId())
                .musicId(source.getMusicId() == null
                        ? new CopyOnWriteArrayList<>()
                        : new CopyOnWriteArrayList<>(source.getMusicId()))
                .channel(source.getChannel())
                .order(source.getOrder())
                .priority(source.getPriority())
                .random(source.getRandom())
                .loop(source.getLoop())
                .enabled(source.getEnabled())
                .overWrite(source.getOverWrite())
                .volume(source.getVolume())
                .pitch(source.getPitch())
                .enterCommands(source.getEnterCommands() == null
                        ? new CopyOnWriteArrayList<>()
                        : new CopyOnWriteArrayList<>(source.getEnterCommands()))
                .exitCommands(source.getExitCommands() == null
                        ? new CopyOnWriteArrayList<>()
                        : new CopyOnWriteArrayList<>(source.getExitCommands()))
                .shape(source.getShape())
                .minPoint(copyPoint(source.getMinPoint()))
                .maxPoint(copyPoint(source.getMaxPoint()))
                .build();
    }

    private AreaDto.Point copyPoint(AreaDto.Point source){
        if(source == null){
            return null;
        }
        return AreaDto.Point.builder()
                .x(source.getX())
                .y(source.getY())
                .z(source.getZ())
                .build();
    }

    private void renderVisualizations(){
        renderSessions();
        renderPreviews();
    }

    private void renderSessions(){
        for(Map.Entry<UUID, RegionEditSession> entry : manager.snapshot()){
            Player player = Bukkit.getPlayer(entry.getKey());
            RegionEditSession session = entry.getValue();
            if(player == null || !player.isOnline()){
                manager.end(entry.getKey());
                originalAreas.remove(entry.getKey());
                continue;
            }
            if(!player.getWorld().getName().equals(session.getWorldName())){
                cancelImmediately(player, true);
                continue;
            }
            renderSession(player, session);
        }
    }

    private void renderPreviews(){
        for(Map.Entry<UUID, RegionPreviewManager.Target> entry
                : previewManager.snapshot().entrySet()){
            UUID playerUuid = entry.getKey();
            RegionPreviewManager.Target target = entry.getValue();
            Player player = Bukkit.getPlayer(playerUuid);
            if(player == null || !player.isOnline()){
                clearAreaPreview(playerUuid);
                continue;
            }
            if(manager.get(playerUuid) != null
                    || !player.getWorld().getName().equals(target.getWorldName())){
                clearAreaPreview(playerUuid);
                continue;
            }

            AreaDto area = plugin.getConfigManager().findArea(
                    target.getWorldName(),
                    target.getAreaId()
            );
            if(area == null || area.getShape() == null){
                clearAreaPreview(playerUuid);
                player.sendMessage("§e[RookieAreaMusic] 区域已不存在，轮廓预览已关闭: "
                        + target.getWorldName() + "/" + target.getAreaId());
                continue;
            }

            try {
                PreviewRenderState state = previewRenderStates.get(playerUuid);
                if(state == null
                        || state.area != area
                        || state.shape != area.getShape()){
                    state = new PreviewRenderState(
                            area,
                            area.getShape(),
                            RegionPreviewWireframe.from(area.getShape().getConfig())
                    );
                    previewRenderStates.put(playerUuid, state);
                }
                renderPreview(player, target, state);
            } catch (RuntimeException e){
                clearAreaPreview(playerUuid);
                plugin.getLogger().log(
                        Level.WARNING,
                        "区域轮廓预览渲染失败: "
                                + target.getWorldName() + "/" + target.getAreaId(),
                        e
                );
                player.sendMessage("§c[RookieAreaMusic] 区域轮廓渲染失败，预览已关闭: "
                        + deepestMessage(e));
            }
        }
    }

    private void renderPreview(Player player,
                               RegionPreviewManager.Target target,
                               PreviewRenderState state){
        double phase = state.nextPhase();
        for(RegionPreviewWireframe.Sample sample
                : state.wireframe.sample(PARTICLE_BUDGET, phase)){
            player.spawnParticle(
                    Particle.DUST,
                    new Location(
                            player.getWorld(),
                            sample.getX(),
                            sample.getY(),
                            sample.getZ()
                    ),
                    1,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    previewDust(sample.getStyle()),
                    true
            );
        }

        String status = "§dROI 轮廓 §f" + target.getWorldName() + "/" + target.getAreaId()
                + " §7| §eY=[" + formatCoordinate(state.wireframe.getMinY())
                + "," + formatCoordinate(state.wireframe.getMaxExclusiveY()) + ")"
                + " §7| §b切片=" + state.wireframe.getSliceCount();
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(status));
    }

    private Particle.DustOptions previewDust(RegionPreviewWireframe.Style style){
        switch (style){
            case BASE:
                return PREVIEW_BASE_DUST;
            case TRANSITION_TOP:
                return PREVIEW_TRANSITION_DUST;
            case VERTICAL:
                return PREVIEW_VERTICAL_DUST;
            case FINAL_TOP:
                return PREVIEW_FINAL_TOP_DUST;
            default:
                return PREVIEW_FALLBACK_DUST;
        }
    }

    private void renderSession(Player player, RegionEditSession session){
        ParticleBudget budget = new ParticleBudget(PARTICLE_BUDGET);
        String validation = session.currentValidationError();
        Color currentColor = validation == null
                ? Color.LIME
                : Color.RED;
        if(!session.isAwaitingHeight()){
            drawPolygon(
                    player,
                    session.getDraft(),
                    session.getCurrentY(),
                    currentColor,
                    validation == null ? Color.WHITE : Color.RED,
                    true,
                    budget
            );
        }
        for(Map.Entry<Integer, List<RegionShapeConfig.Point>> entry
                : session.getSavedSlices().entrySet()){
            if(entry.getKey().equals(session.getCurrentY())){
                continue;
            }
            drawPolygon(
                    player,
                    entry.getValue(),
                    entry.getKey(),
                    Color.GRAY,
                    Color.GRAY,
                    false,
                    budget
            );
            if(budget.remaining <= 0){
                break;
            }
        }

        String status = "§aROI §f" + session.getWorldName() + "/" + session.getAreaId()
                + " §7| §eY=" + (session.isAwaitingHeight()
                        ? "待首次点击 blockY"
                        : session.getCurrentY())
                + " §7| §b切片=" + session.getSavedSliceCount()
                + " §7| §d顶点=" + session.getDraft().size();
        if(validation != null){
            status += " §7| §c" + validation;
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(status));
    }

    private void drawPolygon(Player player,
                             List<RegionShapeConfig.Point> points,
                             int y,
                             Color edgeColor,
                             Color vertexColor,
                             boolean vertices,
                             ParticleBudget budget){
        if(points == null || points.isEmpty() || budget.remaining <= 0){
            return;
        }
        World world = player.getWorld();
        Particle.DustOptions edgeDust = new Particle.DustOptions(edgeColor, 0.7f);
        if(vertices){
            Particle.DustOptions vertexDust = new Particle.DustOptions(vertexColor, 1.2f);
            for(RegionShapeConfig.Point point : points){
                if(!budget.take()){
                    return;
                }
                player.spawnParticle(
                        Particle.DUST,
                        new Location(world, point.getX(), y + 0.12, point.getZ()),
                        1,
                        vertexDust
                );
            }
        }
        if(points.size() < 2){
            return;
        }

        int edgeCount = points.size() >= 3 ? points.size() : points.size() - 1;
        double totalLength = 0.0;
        for(int index = 0; index < edgeCount; index++){
            RegionShapeConfig.Point first = points.get(index);
            RegionShapeConfig.Point second = points.get((index + 1) % points.size());
            totalLength += distance(first, second);
        }
        double step = Math.max(0.5, totalLength / Math.max(1, budget.remaining));
        for(int index = 0; index < edgeCount && budget.remaining > 0; index++){
            RegionShapeConfig.Point first = points.get(index);
            RegionShapeConfig.Point second = points.get((index + 1) % points.size());
            double length = distance(first, second);
            int samples = Math.max(1, (int) Math.ceil(length / step));
            for(int sample = 0; sample <= samples && budget.take(); sample++){
                double progress = samples == 0 ? 0.0 : (double) sample / samples;
                player.spawnParticle(
                        Particle.DUST,
                        new Location(
                                world,
                                first.getX() + (second.getX() - first.getX()) * progress,
                                y + 0.12,
                                first.getZ() + (second.getZ() - first.getZ()) * progress
                        ),
                        1,
                        edgeDust
                );
            }
        }
    }

    private double distance(RegionShapeConfig.Point first,
                            RegionShapeConfig.Point second){
        double x = second.getX() - first.getX();
        double z = second.getZ() - first.getZ();
        return Math.sqrt(x * x + z * z);
    }

    private boolean isValidAreaId(String areaId){
        return areaId != null && areaId.matches("[A-Za-z0-9._-]+");
    }

    private boolean isBlank(String value){
        return value == null || value.trim().isEmpty();
    }

    private String formatCoordinate(double value){
        long integer = (long) value;
        if(value == integer){
            return Long.toString(integer);
        }
        return Double.toString(value);
    }

    private String deepestMessage(Throwable throwable){
        Throwable current = throwable;
        String message = throwable.getClass().getSimpleName();
        while(current != null){
            if(current.getMessage() != null && !current.getMessage().trim().isEmpty()){
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return message;
    }

    private static final class ParticleBudget {
        private int remaining;

        private ParticleBudget(int remaining) {
            this.remaining = remaining;
        }

        private boolean take(){
            if(remaining <= 0){
                return false;
            }
            remaining--;
            return true;
        }
    }

    private static final class PreviewRenderState {
        private static final double PHASE_STEP = 0.3819660112501051;

        private final AreaDto area;
        private final SlicedPolygonVolume shape;
        private final RegionPreviewWireframe wireframe;
        private double phase;

        private PreviewRenderState(AreaDto area,
                                   SlicedPolygonVolume shape,
                                   RegionPreviewWireframe wireframe) {
            this.area = area;
            this.shape = shape;
            this.wireframe = wireframe;
        }

        private double nextPhase(){
            double result = phase;
            phase = (phase + PHASE_STEP) % 1.0;
            return result;
        }
    }
}
