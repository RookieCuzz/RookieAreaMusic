package io.github.rookiecuzz.rookieareamusic.command;

import io.github.rookiecuzz.rookieareamusic.RookieAreaMusic;
import io.github.rookiecuzz.rookieareamusic.config.AreaDto;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

/** Executes trusted, region-owned lifecycle commands on the Bukkit main thread. */
public final class EntryCommandDispatcher {
    public enum ActionType {
        ENTER("入场"),
        EXIT("离场");

        private final String displayName;

        ActionType(String displayName) {
            this.displayName = displayName;
        }
    }

    static final int MAX_EXPANDED_COMMAND_LENGTH = 4096;
    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile(
            "%[^%]+%"
    );

    private final RookieAreaMusic plugin;
    private final Method placeholderApiMethod;
    private volatile boolean commandsEnabled;

    public EntryCommandDispatcher(RookieAreaMusic plugin) {
        if(plugin == null){
            throw new IllegalArgumentException("plugin 不能为空");
        }
        this.plugin = plugin;
        this.placeholderApiMethod = findPlaceholderApiMethod(plugin);
        this.commandsEnabled = plugin.getConfig().getBoolean(
                "actions.commands.enabled",
                true
        );
    }

    /** Publishes the setting only after the plugin has committed a reload. */
    public void setCommandsEnabled(boolean commandsEnabled){
        this.commandsEnabled = commandsEnabled;
    }

    public boolean dispatch(Player player,
                            AreaDto area,
                            List<String> commandTemplates){
        return dispatch(player, area, commandTemplates, ActionType.ENTER);
    }

    public boolean dispatch(Player player,
                            AreaDto area,
                            List<String> commandTemplates,
                            ActionType actionType){
        if(player == null
                || area == null
                || commandTemplates == null
                || commandTemplates.isEmpty()
                || !commandsEnabled){
            return false;
        }

        Location location = player.getLocation();
        World world = location.getWorld();
        if(world == null){
            return false;
        }
        PlaceholderValues values = new PlaceholderValues(
                player.getName(),
                player.getUniqueId(),
                world.getName(),
                area.getWorld(),
                area.getAreaId(),
                area.getUuid(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
        ActionType safeActionType = actionType == null
                ? ActionType.ENTER
                : actionType;
        boolean acceptedAny = false;

        for(int index = 0; index < commandTemplates.size(); index++){
            if(!player.isOnline() || !plugin.isEnabled()){
                return acceptedAny;
            }
            String command = expandBuiltIns(commandTemplates.get(index), values);
            try {
                command = expandPlaceholderApi(player, command);
            } catch (ReflectiveOperationException | RuntimeException e){
                logFailure(
                        area,
                        player,
                        index,
                        safeActionType,
                        "PlaceholderAPI 展开失败",
                        e
                );
                continue;
            }
            if(!isSafeExpandedCommand(command)){
                logFailure(
                        area,
                        player,
                        index,
                        safeActionType,
                        "命令为空、过长、含控制字符或仍有未解析占位符",
                        null
                );
                continue;
            }

            try {
                if(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)){
                    acceptedAny = true;
                } else {
                    logFailure(
                            area,
                            player,
                            index,
                            safeActionType,
                            "命令未被服务器接受",
                            null
                    );
                }
            } catch (RuntimeException e){
                logFailure(
                        area,
                        player,
                        index,
                        safeActionType,
                        "命令执行异常",
                        e
                );
            }
        }
        return acceptedAny;
    }

    static String expandBuiltIns(String template,
                                 PlaceholderValues values){
        if(template == null || values == null){
            return "";
        }
        Map<String, String> replacements = values.asMap();
        StringBuilder result = new StringBuilder(template.length() + 32);
        int offset = 0;
        while(offset < template.length()){
            boolean replaced = false;
            for(Map.Entry<String, String> replacement : replacements.entrySet()){
                String token = replacement.getKey();
                if(template.startsWith(token, offset)){
                    result.append(replacement.getValue());
                    offset += token.length();
                    replaced = true;
                    break;
                }
            }
            if(!replaced){
                result.append(template.charAt(offset));
                offset++;
            }
        }
        return result.toString();
    }

    static boolean isSafeExpandedCommand(String command){
        if(command == null
                || command.trim().isEmpty()
                || command.length() > MAX_EXPANDED_COMMAND_LENGTH
                || containsForbiddenControlCharacter(command)){
            return false;
        }
        return !UNRESOLVED_PLACEHOLDER.matcher(command).find();
    }

    private String expandPlaceholderApi(Player player,
                                        String command)
            throws ReflectiveOperationException {
        if(placeholderApiMethod == null){
            return command;
        }
        try {
            Object expanded = placeholderApiMethod.invoke(null, player, command);
            return expanded instanceof String ? (String) expanded : command;
        } catch (InvocationTargetException e){
            Throwable cause = e.getCause();
            if(cause instanceof RuntimeException){
                throw (RuntimeException) cause;
            }
            throw e;
        }
    }

    private static Method findPlaceholderApiMethod(RookieAreaMusic plugin){
        if(!plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")){
            return null;
        }
        try {
            Class<?> api = Class.forName(
                    "me.clip.placeholderapi.PlaceholderAPI",
                    true,
                    plugin.getClass().getClassLoader()
            );
            return api.getMethod(
                    "setPlaceholders",
                    OfflinePlayer.class,
                    String.class
            );
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e){
            plugin.getLogger().log(
                    Level.WARNING,
                    "检测到 PlaceholderAPI，但无法启用占位符桥接；带未解析 %...% 的区域命令将被跳过",
                    e
            );
            return null;
        }
    }

    private static boolean containsForbiddenControlCharacter(String command){
        for(int index = 0; index < command.length(); index++){
            char value = command.charAt(index);
            if(value == '\u0000' || value == '\r' || value == '\n'){
                return true;
            }
        }
        return false;
    }

    private void logFailure(AreaDto area,
                            Player player,
                            int commandIndex,
                            ActionType actionType,
                            String reason,
                            Throwable error){
        String message = "区域" + actionType.displayName + "命令失败: "
                + area.getWorld() + "/" + area.getAreaId()
                + " #" + (commandIndex + 1)
                + " player=" + player.getUniqueId()
                + " (" + reason + ")";
        if(error == null){
            plugin.getLogger().warning(message);
        } else {
            plugin.getLogger().log(Level.WARNING, message, error);
        }
    }

    static final class PlaceholderValues {
        private final String playerName;
        private final UUID playerUuid;
        private final String worldName;
        private final String areaWorldName;
        private final String areaId;
        private final String areaUuid;
        private final double x;
        private final double y;
        private final double z;
        private final int blockX;
        private final int blockY;
        private final int blockZ;

        PlaceholderValues(String playerName,
                          UUID playerUuid,
                          String worldName,
                          String areaWorldName,
                          String areaId,
                          String areaUuid,
                          double x,
                          double y,
                          double z,
                          int blockX,
                          int blockY,
                          int blockZ) {
            this.playerName = value(playerName);
            this.playerUuid = playerUuid;
            this.worldName = value(worldName);
            this.areaWorldName = value(areaWorldName);
            this.areaId = value(areaId);
            this.areaUuid = value(areaUuid);
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
        }

        private Map<String, String> asMap(){
            Map<String, String> values = new LinkedHashMap<>();
            putAliases(values, playerName,
                    "{player_name}", "%player_name%", "{player}", "%player%");
            putAliases(values, playerUuid == null ? "" : playerUuid.toString(),
                    "{player_uuid}", "%player_uuid%");
            putAliases(values, worldName, "{world}", "%world%");
            putAliases(values, areaWorldName, "{area_world}", "%area_world%");
            putAliases(values, areaId, "{area_id}", "%area_id%", "{area}", "%area%");
            putAliases(values, areaUuid, "{area_uuid}", "%area_uuid%");
            putAliases(values, Double.toString(x), "{x}", "%x%");
            putAliases(values, Double.toString(y), "{y}", "%y%");
            putAliases(values, Double.toString(z), "{z}", "%z%");
            putAliases(values, Integer.toString(blockX), "{block_x}", "%block_x%");
            putAliases(values, Integer.toString(blockY), "{block_y}", "%block_y%");
            putAliases(values, Integer.toString(blockZ), "{block_z}", "%block_z%");
            return values;
        }

        private static void putAliases(Map<String, String> values,
                                       String replacement,
                                       String... aliases){
            for(String alias : aliases){
                values.put(alias, replacement);
            }
        }

        private static String value(String value){
            return value == null ? "" : value;
        }
    }
}
