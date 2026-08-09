package com.gitee.niocho.areamusic.command;

import com.gitee.niocho.areamusic.RookieAreaMusic;
import com.gitee.niocho.areamusic.config.AreaDto;
import com.gitee.niocho.areamusic.config.ConfigManager;
import com.gitee.niocho.areamusic.config.MusicDto;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public class MainExecutor implements CommandExecutor {
    private final RookieAreaMusic plugin;

    public MainExecutor(RookieAreaMusic plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(args.length == 0){
            sender.sendMessage("\u00A7c[RookieAreaMusic] 请使用 /am help 来参阅帮助");
            return true;
        }

        if(!sender.hasPermission("area-music.admin")){
            sender.sendMessage("\u00A7c[RookieAreaMusic] 权限不足");
            return true;
        }

        switch (args[0]){
            case "music":
                if(args.length < 2){
                    sender.sendMessage("\u00A7c[RookieAreaMusic] 请指定子指令");
                    return true;
                }
                switch (args[1]){
                    case "add":
                        return handleAddMusic(sender, Arrays.copyOfRange(args, 2, args.length));
                    case "del":
                        return handleDelMusic(sender, Arrays.copyOfRange(args, 2, args.length));
                    case "list":
                        return handleListMusic(sender, Arrays.copyOfRange(args, 2, args.length));
                }
                sender.sendMessage("\u00A7c[RookieAreaMusic] 未匹配到 music 的子指令");
                return true;
            case "area":
                if(args.length < 2){
                    sender.sendMessage("\u00A7c[RookieAreaMusic] 请指定子指令");
                    return true;
                }
                switch (args[1]){
                    case "create":
                        return handleCreateArea(sender, Arrays.copyOfRange(args, 2, args.length));
                    case "edit":
                        return handleEditArea(sender, Arrays.copyOfRange(args, 2, args.length));
                    case "editor":
                        return handleAreaEditor(sender, Arrays.copyOfRange(args, 2, args.length));
                    case "del":
                        return handleDelArea(sender, Arrays.copyOfRange(args, 2, args.length));
                    case "list":
                        return handleListArea(sender, Arrays.copyOfRange(args, 2, args.length));
                }
                sender.sendMessage("\u00A7c[RookieAreaMusic] 未匹配到 area 的子指令");
                return true;
            case "reload":
                return handleReload(sender);
            case "help":
                return handleHelp(sender);
        }

        sender.sendMessage("\u00A7c[RookieAreaMusic] 未匹配到指令");
        return true;
    }

    private boolean handleAddMusic(CommandSender sender, String[] args){
        if (args.length != 5){
            sender.sendMessage("\u00A7c[RookieAreaMusic] 用法: /am music add <世界> <区域> <音乐ID> <声音键> <秒数>");
            return true;
        }

        try {
            String worldName = args[0];
            String areaId = args[1];
            String musicId = args[2];
            String musicURL = args[3];
            Long duration = Long.parseLong(args[4]);
            if(musicId.trim().isEmpty() || musicURL.trim().isEmpty() || duration <= 0){
                sender.sendMessage("\u00A7c[RookieAreaMusic] 参数错误");
                return true;
            }
            if(plugin.getRegionEditorService().isRegionLocked(worldName, areaId)){
                sender.sendMessage("§c[RookieAreaMusic] 该区域正在编辑，暂时不能修改音乐");
                return true;
            }

            AreaDto area = plugin.getConfigManager().findArea(worldName, areaId);
            if(area == null){
                sender.sendMessage("\u00A7c[RookieAreaMusic] 未找到区域 " + worldName + "/" + areaId);
                return true;
            }
            if(area.getMusicId() == null){
                area.setMusicId(new CopyOnWriteArrayList<>());
            }
            for(String musicUuid : area.getMusicId()){
                MusicDto existing = plugin.getConfigManager().getMusics().get(musicUuid);
                if(existing != null && existing.getMusicId().equals(musicId)){
                    sender.sendMessage("\u00A7c[RookieAreaMusic] 该区域已经存在音乐 " + musicId);
                    return true;
                }
            }

            String uuid = ConfigManager.createTrackUuid(worldName, areaId, musicId);
            Map<String, MusicDto> musics = this.plugin.getConfigManager().getMusics();
            musics.put(uuid, MusicDto.builder()
                    .uuid(uuid)
                    .musicId(musicId)
                    .musicURL(musicURL)
                    .musicDuration(duration)
                    .build());
            area.getMusicId().add(uuid);
            if(!persistChanges(sender, () -> {
                musics.remove(uuid);
                area.getMusicId().remove(uuid);
            })){
                return true;
            }
            plugin.rebuildSpatialIndex();
            sender.sendMessage("\u00A72[RookieAreaMusic] 音乐 " + musicId + " 已添加到 "
                    + worldName + "/" + areaId);
            return true;
        } catch (NumberFormatException e){
            sender.sendMessage("\u00A7c[RookieAreaMusic] 输入的 musicDuration 无效");
            return true;
        }
    }

    private boolean handleDelMusic(CommandSender sender, String[] args){
        if(args.length != 3){
            sender.sendMessage("\u00A7c[RookieAreaMusic] 用法: /am music del <世界> <区域> <音乐ID>");
            return true;
        }

        String worldName = args[0];
        String areaId = args[1];
        String musicId = args[2];
        if(plugin.getRegionEditorService().isRegionLocked(worldName, areaId)){
            sender.sendMessage("§c[RookieAreaMusic] 该区域正在编辑，暂时不能修改音乐");
            return true;
        }
        AreaDto area = plugin.getConfigManager().findArea(worldName, areaId);
        if(area == null){
            sender.sendMessage("\u00A7c[RookieAreaMusic] 未找到区域 " + worldName + "/" + areaId);
            return true;
        }

        String matchedUuid = null;
        if(area.getMusicId() != null){
            for(String musicUuid : area.getMusicId()){
                MusicDto music = plugin.getConfigManager().getMusics().get(musicUuid);
                if(music != null && music.getMusicId().equals(musicId)){
                    matchedUuid = musicUuid;
                    break;
                }
            }
        }
        if(matchedUuid == null){
            sender.sendMessage("\u00A7c[RookieAreaMusic] 区域中不存在音乐 " + musicId);
            return true;
        }

        final String removedUuid = matchedUuid;
        final int removedIndex = area.getMusicId().indexOf(removedUuid);
        MusicDto removedMusic = plugin.getConfigManager().getMusics().remove(removedUuid);
        area.getMusicId().remove(removedUuid);
        if(!persistChanges(sender, () -> {
            plugin.getConfigManager().getMusics().put(removedUuid, removedMusic);
            area.getMusicId().add(removedIndex, removedUuid);
        })){
            return true;
        }
        plugin.rebuildSpatialIndex();
        sender.sendMessage("\u00A72[RookieAreaMusic] 已从 " + worldName + "/" + areaId
                + " 删除音乐 " + musicId);
        return true;
    }

    private boolean handleListMusic(CommandSender sender, String[] args){
        if(args.length < 2 || args.length > 3){
            sender.sendMessage("\u00A7c[RookieAreaMusic] 用法: /am music list <世界> <区域> [页码]");
            return true;
        }
        String worldName = args[0];
        String areaId = args[1];
        AreaDto area = plugin.getConfigManager().findArea(worldName, areaId);
        if(area == null){
            sender.sendMessage("\u00A7c[RookieAreaMusic] 未找到区域 " + worldName + "/" + areaId);
            return true;
        }

        int page = 0;
        if(args.length == 3){
            try {
                page = Integer.parseInt(args[2]);
                if(page < 0){
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e){
                sender.sendMessage("\u00A7c[RookieAreaMusic] 页数错误");
                return true;
            }
        }

        sender.sendMessage(String.format("\u00A7a[RookieAreaMusic] %s/%s 音乐列表（页码 %d）:",
                worldName, areaId, page));
        getPagedListMusic(area, page).forEach((musicDto -> {
            TextComponent textComponent = new TextComponent(musicDto.getMusicId());
            textComponent.setUnderlined(true);
            textComponent.setColor(ChatColor.WHITE);
            textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("音乐插件内部 ID: " + musicDto.getUuid() + "\n" + "音乐的客户端 ID: " + musicDto.getMusicURL() + "\n" + "音乐的持续时间: " + musicDto.getMusicDuration() + " s")));
            sender.spigot().sendMessage(textComponent);
        }));
        TextComponent textComponentFront = new TextComponent("");
        if(page != 0){
            TextComponent textComponent = new TextComponent("<- 上一页 ");
            textComponent.setColor(ChatColor.GREEN);
            textComponent.setUnderlined(false);
            textComponent.setClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/am music list " + worldName + " " + areaId + " " + (page - 1)
            ));
            textComponentFront.addExtra(textComponent);
        }
        TextComponent textComponent = new TextComponent(" 下一页 ->");
        textComponent.setColor(ChatColor.GREEN);
        textComponent.setUnderlined(false);
        textComponent.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/am music list " + worldName + " " + areaId + " " + (page + 1)
        ));
        textComponentFront.addExtra(textComponent);
        sender.spigot().sendMessage(textComponentFront);
        return true;
    }

    private List<MusicDto> getPagedListMusic(AreaDto area, int page){
        List<MusicDto> musicList = new ArrayList<>();

        int from = page * 9;
        int to = page * 9 + 8;

        int count = 0;

        if(area.getMusicId() == null){
            return musicList;
        }
        for(String musicUuid : area.getMusicId()){
            if(from <= count && count <= to){
                MusicDto music = this.plugin.getConfigManager().getMusics().get(musicUuid);
                if(music != null){
                    musicList.add(music);
                }
            }
            count ++;
        }

        return musicList;
    }

    private boolean handleCreateArea(CommandSender sender, String[] args){
        if(!(sender instanceof Player)){
            sender.sendMessage("§c[RookieAreaMusic] 该命令只能由玩家执行");
            return true;
        }
        if(args.length != 1){
            sender.sendMessage("§c[RookieAreaMusic] 用法: /am area create <区域ID>");
            return true;
        }
        plugin.getRegionEditorService().beginCreate((Player) sender, args[0]);
        return true;
    }

    private boolean handleEditArea(CommandSender sender, String[] args){
        if(!(sender instanceof Player)){
            sender.sendMessage("§c[RookieAreaMusic] 该命令只能由玩家执行");
            return true;
        }
        if(args.length != 2){
            sender.sendMessage("§c[RookieAreaMusic] 用法: /am area edit <世界> <区域ID>");
            return true;
        }
        plugin.getRegionEditorService().beginEdit((Player) sender, args[0], args[1]);
        return true;
    }

    private boolean handleAreaEditor(CommandSender sender, String[] args){
        if(!(sender instanceof Player)){
            sender.sendMessage("§c[RookieAreaMusic] 该命令只能由玩家执行");
            return true;
        }
        if(args.length != 1){
            sender.sendMessage("§c[RookieAreaMusic] 用法: /am area editor <finish|cancel>");
            return true;
        }
        Player player = (Player) sender;
        if("finish".equalsIgnoreCase(args[0])){
            plugin.getRegionEditorService().finish(player);
        } else if("cancel".equalsIgnoreCase(args[0])){
            plugin.getRegionEditorService().requestCancel(player);
        } else {
            sender.sendMessage("§c[RookieAreaMusic] 用法: /am area editor <finish|cancel>");
        }
        return true;
    }

    private boolean handleDelArea(CommandSender sender, String[] args){
        if(args.length != 2){
            sender.sendMessage("\u00A7c[RookieAreaMusic] 用法: /am area del <世界> <区域>");
            return true;
        }

        String worldName = args[0];
        String areaId = args[1];
        if(plugin.getRegionEditorService().isRegionLocked(worldName, areaId)){
            sender.sendMessage("§c[RookieAreaMusic] 该区域正在编辑，请先完成或取消编辑");
            return true;
        }
        AreaDto area = plugin.getConfigManager().findArea(worldName, areaId);
        if(area == null){
            sender.sendMessage("\u00A7c[RookieAreaMusic] 未找到区域 " + worldName + "/" + areaId);
            return true;
        }

        Map<String, AreaDto> worldAreas = plugin.getConfigManager().getAreas().get(worldName);
        Map<String, MusicDto> removedMusics = new HashMap<>();
        if(area.getMusicId() != null){
            for(String musicUuid : area.getMusicId()){
                MusicDto removedMusic = plugin.getConfigManager().getMusics().remove(musicUuid);
                if(removedMusic != null){
                    removedMusics.put(musicUuid, removedMusic);
                }
            }
        }
        worldAreas.remove(area.getUuid());

        try {
            plugin.getConfigManager().deleteRegionFiles(worldName, areaId);
        } catch (IOException e){
            worldAreas.put(area.getUuid(), area);
            plugin.getConfigManager().getMusics().putAll(removedMusics);
            plugin.getLogger().log(Level.SEVERE, "RookieAreaMusic 区域文件删除失败，内存修改已回滚", e);
            sender.sendMessage("\u00A7c[RookieAreaMusic] 删除失败，修改已回滚: " + getErrorMessage(e));
            return true;
        }

        plugin.rebuildSpatialIndex();
        sender.sendMessage("\u00A72[RookieAreaMusic] 已删除区域 " + worldName + "/" + areaId
                + "，原文件已移入 .deleted-regions");
        return true;
    }

    private boolean handleListArea(CommandSender sender, String[] args){
        int page = 0;
        if(args.length != 0){
            try {
                page = Integer.parseInt(args[0]);
                if(page < 0){
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e){
                sender.sendMessage("\u00A7c[RookieAreaMusic] 页数错误");
                return true;
            }
        }

        sender.sendMessage(String.format("\u00A7a[RookieAreaMusic] 区域的列表如下（页码 %d）:", page));
        getPagedListArea(page).forEach((areaDto -> {
            TextComponent textComponent = new TextComponent(areaDto.getAreaId());
            textComponent.setUnderlined(true);
            textComponent.setColor(ChatColor.WHITE);
            textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(
                    "区域插件内部 ID: " + areaDto.getUuid() + "\n" +
                            "区域 ID: " + areaDto.getAreaId() + "\n" +
                            "区域所在世界: " + areaDto.getWorld() + "\n" +
                            "播放频道: " + areaDto.getChannel() + "\n" +
                            "同优先级排序值: " + areaDto.getOrder() + "\n" +
                            "区域所播放的音乐 ID:\n" + getMusicWithUUID(areaDto.getMusicId()) + "\n" +
                            "区域起始位点: " + areaDto.getMinPoint().getX() + " " + areaDto.getMinPoint().getY() + " " + areaDto.getMinPoint().getZ() + "\n" +
                            "区域结束位点: " + areaDto.getMaxPoint().getX() + " " + areaDto.getMaxPoint().getY() + " " + areaDto.getMaxPoint().getZ()
            )));
            sender.spigot().sendMessage(textComponent);
        }));
        TextComponent textComponentFront = new TextComponent("");
        if(page != 0){
            TextComponent textComponent = new TextComponent("<- 上一页 ");
            textComponent.setColor(ChatColor.GREEN);
            textComponent.setUnderlined(false);
            textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/am area list " + (page - 1)));
            textComponentFront.addExtra(textComponent);
        }
        TextComponent textComponent = new TextComponent(" 下一页 ->");
        textComponent.setColor(ChatColor.GREEN);
        textComponent.setUnderlined(false);
        textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/am area list " + (page +1)));
        textComponentFront.addExtra(textComponent);
        sender.spigot().sendMessage(textComponentFront);
        return true;
    }

    private String getMusicWithUUID(List<String> uuids){
        StringBuilder finalStr = new StringBuilder();
        for (String uuid : uuids) {
            MusicDto musicDto = plugin.getConfigManager().getMusics().get(uuid);
            if(musicDto == null){
                finalStr.append(uuid).append("\n");
                continue;
            }
            finalStr.append(musicDto.getMusicId()).append("\n");
        }

        return finalStr.toString();
    }

    private List<AreaDto> getPagedListArea(int page){
        List<AreaDto> areaList = new ArrayList<>();

        int from = page * 9;
        int to = page * 9 + 8;

        final int[] count = {0};

        this.plugin.getConfigManager().getAreas().forEach((world, worldAreas) -> {
            for(Map.Entry<String, AreaDto> item: worldAreas.entrySet()){
                if(from <= count[0] && count[0] <= to){
                    areaList.add(item.getValue());
                }
                count[0]++;
            }
        });

        return areaList;
    }

    private boolean handleReload(CommandSender sender){
        sender.sendMessage("\u00A72[RookieAreaMusic] 正在尝试重载...");
        try {
            plugin.onReload();
            sender.sendMessage("\u00A72[RookieAreaMusic] 重载成功！");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "RookieAreaMusic 重载失败", e);
            sender.sendMessage("\u00A7c[RookieAreaMusic] 重载失败: " + getErrorMessage(e));
        }
        return true;
    }

    private boolean handleHelp(CommandSender sender){
        sender.sendMessage("§2[RookieAreaMusic] 管理命令");
        sender.sendMessage("§f/am area create <区域ID> §7- 新建 CT 切片区域");
        sender.sendMessage("§f/am area edit <世界> <区域ID> §7- 编辑区域形状");
        sender.sendMessage("§f/am area editor <finish|cancel> §7- 完成或取消编辑");
        sender.sendMessage("§f/am area list [页码] §7- 列出区域");
        sender.sendMessage("§f/am area del <世界> <区域ID> §7- 删除区域并保留回收备份");
        sender.sendMessage("§f/am music <add|del|list> ... §7- 管理区域曲目");
        sender.sendMessage("§f/am reload §7- 原子重载配置");
        sender.sendMessage("§7完整说明：README.md、CONFIG_GUIDE.zh-CN.md");
        return true;
    }

    private boolean persistChanges(CommandSender sender, Runnable rollback){
        try {
            plugin.getConfigManager().save();
            return true;
        } catch (IOException e){
            rollback.run();
            plugin.getLogger().log(Level.SEVERE, "RookieAreaMusic 配置保存失败，内存修改已回滚", e);
            sender.sendMessage("\u00A7c[RookieAreaMusic] 保存失败，修改已回滚: " + getErrorMessage(e));
            return false;
        }
    }

    private String getErrorMessage(Throwable throwable){
        Throwable current = throwable;
        String message = null;
        while(current != null){
            if(current.getMessage() != null && !current.getMessage().trim().isEmpty()){
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return message == null ? throwable.getClass().getSimpleName() : message;
    }
}
