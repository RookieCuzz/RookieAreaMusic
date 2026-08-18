package io.github.rookiecuzz.rookieregions.bukkit;

import io.github.rookiecuzz.rookieregions.rule.Subject;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class BukkitSubjects {
    private static final String GROUP_PREFIX = "rookieregions.group.";
    private static final Set<String> IMPORTANT_PERMISSIONS = Set.of(
            "rookieregions.admin",
            "rookieregions.region.create",
            "rookieregions.region.edit.own",
            "rookieregions.region.edit.any",
            "rookieregions.region.overlap",
            "rookieregions.region.delete",
            "rookieregions.region.flag",
            "rookieregions.module.music",
            "rookieregions.module.commands",
            "rookieregions.reload",
            "rookieregions.bypass.build",
            "rookieregions.bypass.block-break",
            "rookieregions.bypass.block-place",
            "rookieregions.bypass.use",
            "rookieregions.bypass.container",
            "rookieregions.bypass.pvp",
            "rookieregions.bypass.entry",
            "rookieregions.bypass.explosion"
    );

    public static Subject from(Player player) {
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        Set<PermissionAttachmentInfo> effective = player.getEffectivePermissions();
        if(effective != null) {
            for (PermissionAttachmentInfo permission : effective) {
                if (!permission.getValue()) {
                    continue;
                }
                String node = permission.getPermission().toLowerCase(Locale.ROOT);
                permissions.add(node);
                if (node.startsWith(GROUP_PREFIX)
                        && node.length() > GROUP_PREFIX.length()) {
                    groups.add(node.substring(GROUP_PREFIX.length()));
                }
            }
        }
        IMPORTANT_PERMISSIONS.stream()
                .filter(player::hasPermission)
                .forEach(permissions::add);
        return new Subject(player.getUniqueId(), groups, permissions);
    }

    private BukkitSubjects() {
    }
}
