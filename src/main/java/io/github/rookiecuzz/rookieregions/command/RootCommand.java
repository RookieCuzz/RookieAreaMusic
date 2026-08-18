package io.github.rookiecuzz.rookieregions.command;

import io.github.rookiecuzz.rookieregions.RookieRegionsPlugin;
import io.github.rookiecuzz.rookieregions.bukkit.BukkitSubjects;
import io.github.rookiecuzz.rookieregions.bukkit.BukkitWorlds;
import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionDomain;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.editor.bukkit.BukkitRegionEditor;
import io.github.rookiecuzz.rookieregions.editor.model.RegionEditSession;
import io.github.rookiecuzz.rookieregions.editor.model.ShapeKind;
import io.github.rookiecuzz.rookieregions.module.music.MusicPolicyMode;
import io.github.rookiecuzz.rookieregions.module.music.MusicTrack;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicChannel;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicProfile;
import io.github.rookiecuzz.rookieregions.mutation.ConfirmationOption;
import io.github.rookiecuzz.rookieregions.mutation.MutationPermissions;
import io.github.rookiecuzz.rookieregions.mutation.RegionMutationActor;
import io.github.rookiecuzz.rookieregions.mutation.RegionSaveOutcome;
import io.github.rookiecuzz.rookieregions.mutation.RegionSaveRequest;
import io.github.rookiecuzz.rookieregions.mutation.RegionSaveRequests;
import io.github.rookiecuzz.rookieregions.mutation.SaveChoice;
import io.github.rookiecuzz.rookieregions.rule.Flag;
import io.github.rookiecuzz.rookieregions.rule.FlagValue;
import io.github.rookiecuzz.rookieregions.rule.Subject;
import io.github.rookiecuzz.rookieregions.runtime.ModuleKind;
import io.github.rookiecuzz.rookieregions.runtime.ModuleRegionBinding;
import io.github.rookiecuzz.rookieregions.runtime.ProviderRegionReference;
import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;
import io.github.rookiecuzz.rookieregions.provider.RegionProvider;
import io.github.rookiecuzz.rookieregions.provider.NativeRegionProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** `/rr` command tree. The root itself deliberately has no permission gate. */
public final class RootCommand implements CommandExecutor, TabCompleter {
    private static final List<String> REGION_COMMANDS = List.of(
            "create", "edit", "delete", "info", "list", "priority",
            "parent", "owner", "member", "flag", "editor"
    );
    private static final List<String> EDITOR_COMMANDS = List.of(
            "finish", "cancel", "undo", "clear", "slice", "min-y", "max-y"
    );
    private static final List<String> MUSIC_POLICIES = List.of(
            "inherit", "block", "add", "replace"
    );

    private final RookieRegionsPlugin plugin;
    private final BukkitRegionEditor editor;
    private final RegionAdministrationService administration;

    public RootCommand(RookieRegionsPlugin plugin,
                       BukkitRegionEditor editor,
                       RegionAdministrationService administration) {
        if(plugin == null || editor == null || administration == null){
            throw new IllegalArgumentException(
                    "plugin, editor, and administration service cannot be null"
            );
        }
        this.plugin = plugin;
        this.editor = editor;
        this.administration = administration;
    }

    public void register(){
        PluginCommand command = plugin.getCommand("rr");
        if(command == null){
            throw new IllegalStateException("plugin.yml does not define /rr");
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {
        try {
            if(args.length == 0){
                help(sender);
                return true;
            }
            switch(lower(args[0])){
                case "region" -> region(sender, args);
                case "music" -> music(sender, args);
                case "module" -> module(sender, args);
                case "reload" -> reload(sender);
                case "help" -> help(sender);
                default -> error(sender, "未知子命令。使用 /rr help");
            }
        } catch (IllegalArgumentException | IllegalStateException exception){
            error(sender, safeMessage(exception));
        }
        return true;
    }

    private void region(CommandSender sender, String[] args){
        if(args.length < 2){
            regionHelp(sender);
            return;
        }
        switch(lower(args[1])){
            case "create" -> create(sender, args);
            case "edit" -> edit(sender, args);
            case "delete" -> delete(sender, args);
            case "info" -> info(sender, args);
            case "list" -> list(sender);
            case "priority" -> priority(sender, args);
            case "parent" -> parent(sender, args);
            case "owner" -> domain(sender, args, true);
            case "member" -> domain(sender, args, false);
            case "flag" -> flag(sender, args);
            case "editor" -> editor(sender, args);
            default -> regionHelp(sender);
        }
    }

    private void create(CommandSender sender, String[] args){
        requirePermission(sender, MutationPermissions.CREATE);
        Player player = requirePlayer(sender);
        if(args.length == 3 && isGlobalAlias(args[2])){
            createGlobal(player);
            return;
        }
        if(args.length != 4){
            throw new IllegalArgumentException(
                    "用法: /rr region create <id> <cuboid|polygon|sliced>"
                            + " 或 /rr region create global"
            );
        }
        if(lower(args[3]).equals("global")){
            if(!isGlobalAlias(args[2])){
                throw new IllegalArgumentException(
                        "global 形状只能使用保留 ID global 或 __global__"
                );
            }
            createGlobal(player);
            return;
        }
        if(isGlobalAlias(args[2])){
            throw new IllegalArgumentException(
                    "global 是保留 ID；请使用 /rr region create global"
            );
        }
        if(!plugin.settings().playerCreationEnabled()
                && !sender.hasPermission(MutationPermissions.ADMIN)){
            throw new IllegalStateException("服务器已关闭玩家区域创建");
        }
        ShapeKind kind;
        try {
            kind = ShapeKind.valueOf(args[3].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception){
            throw new IllegalArgumentException(
                    "形状必须是 cuboid、polygon 或 sliced"
            );
        }
        RegionEditSession session = editor.beginCreate(
                player, plugin.api().snapshot(), args[2], kind
        );
        success(sender, "已开始创建 " + session.key().id()
                + "；使用木斧选点，然后执行 /rr region editor finish");
        if(kind == ShapeKind.POLYGON){
            infoLine(sender, "用 editor min-y <y> / max-y <y> 设置上下界");
        } else if(kind == ShapeKind.SLICED){
            infoLine(sender, "用 editor slice <y> 切换层，用 min-y/max-y 设置上下界");
        }
    }

    private void createGlobal(Player player){
        requirePermission(player, MutationPermissions.EDIT_ANY);
        RegionSnapshot snapshot = plugin.api().snapshot();
        Region global = snapshot.graph()
                .global(BukkitWorlds.id(player.getWorld()))
                .orElseThrow(() -> new IllegalStateException(
                        "当前世界没有可用的 global 根区域"
                ));
        RegionSaveRequest request = RegionSaveRequests
                .edit(snapshot, global, global)
                .sessionId("global-command-" + UUID.randomUUID())
                .build();
        infoLine(player, "正在保存覆盖当前整个世界的 global 区域…");
        plugin.mutations().attemptSave(
                request, mutationActor(player).subject()
        ).whenComplete((outcome, failure) -> onMain(() ->
                handleGlobalSaveOutcome(player, outcome, failure)
        ));
    }

    private void edit(CommandSender sender, String[] args){
        Player player = requirePlayer(sender);
        if(args.length != 3){
            throw new IllegalArgumentException("用法: /rr region edit <id>");
        }
        RegionRecord record = requireRecord(sender, args[2]);
        requireCanEdit(sender, record.region());
        RegionEditSession session = editor.beginEdit(
                player, plugin.api().snapshot(), record.region()
        );
        success(sender, "已锁定并编辑 " + session.key().id()
                + "；木斧选点后执行 editor finish");
    }

    private void delete(CommandSender sender, String[] args){
        requirePermission(sender, CommandPermissions.DELETE);
        if(args.length != 3){
            throw new IllegalArgumentException("用法: /rr region delete <id>");
        }
        RegionRecord record = requireRecord(sender, args[2]);
        administration.delete(record.region().key(), mutationActor(sender))
                .whenComplete((result, failure) ->
                onMain(() -> handleAdministration(
                        sender, result, failure, "区域已删除"
                ))
        );
        infoLine(sender, "正在异步删除区域…");
    }

    private void info(CommandSender sender, String[] args){
        requirePermission(sender, CommandPermissions.VIEW);
        if(args.length != 3){
            throw new IllegalArgumentException("用法: /rr region info <id>");
        }
        RegionRecord record = requireRecord(sender, args[2]);
        Region region = record.region();
        sender.sendMessage(ChatColor.GOLD + "--- " + region.key() + " ---");
        infoLine(sender, "shape=" + shapeName(region)
                + ", priority=" + region.priority());
        infoLine(sender, "parent=" + region.parent()
                .map(RegionKey::toString).orElse("none"));
        infoLine(sender, "owners=" + domainText(region.owners())
                + ", members=" + domainText(region.members()));
        infoLine(sender, "flags=" + region.flags().keySet());
        infoLine(sender, "music channels="
                + record.music().getChannels().keySet());
        infoLine(sender, "children="
                + plugin.api().snapshot().graph().children(region.key())
                .stream().map(child -> child.key().id()).toList());
    }

    private void list(CommandSender sender){
        requirePermission(sender, CommandPermissions.VIEW);
        Collection<RegionRecord> source = plugin.api().snapshot()
                .records().values();
        UUID world = sender instanceof Player player
                ? player.getWorld().getUID()
                : null;
        List<Region> regions = source.stream()
                .map(RegionRecord::region)
                .filter(region -> world == null
                        || region.key().world().uuid().equals(world))
                .sorted(Comparator.comparing(Region::key))
                .toList();
        infoLine(sender, regions.isEmpty()
                ? "没有区域"
                : regions.stream().map(region -> region.key().toString()).toList()
                .toString());
    }

    private void priority(CommandSender sender, String[] args){
        if(args.length != 4){
            throw new IllegalArgumentException(
                    "用法: /rr region priority <id> <integer>"
            );
        }
        RegionRecord record = requireRecord(sender, args[2]);
        requireCanEdit(sender, record.region());
        int priority;
        try {
            priority = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception){
            throw new IllegalArgumentException("priority 必须是整数");
        }
        mutateCore(sender, record, region -> copy(
                region, priority, region.owners(), region.members(),
                region.flags()
        ), "priority 已更新");
    }

    private void parent(CommandSender sender, String[] args){
        requirePermission(sender, MutationPermissions.EDIT_ANY);
        if(args.length != 4){
            throw new IllegalArgumentException(
                    "用法: /rr region parent <id> <parent|global>"
            );
        }
        RegionRecord record = requireRecord(sender, args[2]);
        RegionKey parent = lower(args[3]).equals("global")
                || args[3].equals(RegionKey.GLOBAL_ID)
                ? RegionKey.global(record.region().key().world())
                : new RegionKey(record.region().key().world(), args[3]);
        administration.setParent(
                        record.region().key(), parent, mutationActor(sender)
                )
                .whenComplete((result, failure) -> onMain(() ->
                        handleAdministration(
                                sender, result, failure, "父区域已更新"
                        )
                ));
        infoLine(sender, "正在异步校验父子图…");
    }

    private void domain(CommandSender sender,
                        String[] args,
                        boolean owners){
        if(args.length != 5){
            throw new IllegalArgumentException(
                    "用法: /rr region " + (owners ? "owner" : "member")
                            + " <id> <add|remove> <uuid|online-player|group:name>"
            );
        }
        RegionRecord record = requireRecord(sender, args[2]);
        requireCanEdit(sender, record.region());
        boolean add = switch(lower(args[3])){
            case "add" -> true;
            case "remove" -> false;
            default -> throw new IllegalArgumentException(
                    "操作必须是 add 或 remove"
            );
        };
        DomainEntry entry = domainEntry(args[4]);
        mutateCore(sender, record, region -> {
            RegionDomain changed = changeDomain(
                    owners ? region.owners() : region.members(), entry, add
            );
            return copy(
                    region,
                    region.priority(),
                    owners ? changed : region.owners(),
                    owners ? region.members() : changed,
                    region.flags()
            );
        }, (owners ? "owner" : "member") + " 已更新");
    }

    private void flag(CommandSender sender, String[] args){
        requirePermission(sender, CommandPermissions.FLAG);
        if(args.length != 5){
            throw new IllegalArgumentException(
                    "用法: /rr region flag <id> <flag> <value|unset>"
            );
        }
        RegionRecord record = requireRecord(sender, args[2]);
        requireCanEdit(sender, record.region());
        Flag<?> definition = plugin.api().flagRegistry().require(args[3]);
        requirePermission(sender, definition.modificationPermission());
        mutateCore(sender, record, region -> {
            LinkedHashMap<String, FlagValue<?>> flags = new LinkedHashMap<>(
                    region.flags()
            );
            if(lower(args[4]).equals("unset")){
                flags.remove(definition.name());
            } else {
                flags.put(definition.name(), decodeFlag(definition, args[4]));
            }
            return copy(
                    region, region.priority(), region.owners(),
                    region.members(), flags
            );
        }, "flag 已更新");
    }

    private void editor(CommandSender sender, String[] args){
        Player player = requirePlayer(sender);
        if(args.length < 3){
            throw new IllegalArgumentException(
                    "用法: /rr region editor <finish|cancel|undo|clear|slice|min-y|max-y>"
            );
        }
        switch(lower(args[2])){
            case "finish" -> finish(player, args.length >= 4 ? args[3] : null);
            case "cancel" -> {
                editor.cancel(player.getUniqueId());
                success(sender, "编辑已取消并释放区域锁");
            }
            case "undo" -> {
                boolean removed = editor.undo(player.getUniqueId()).isPresent();
                infoLine(sender, removed ? "已撤销最后一个点" : "当前没有可撤销的点");
            }
            case "clear" -> infoLine(sender, "已清除 "
                    + editor.clear(player.getUniqueId()) + " 个点");
            case "slice" -> {
                requireLength(args, 4, "用法: /rr region editor slice <y>");
                editor.selectSlice(player.getUniqueId(), finiteDouble(args[3], "slice Y"));
                success(sender, "当前切片已切换到 Y=" + args[3]);
            }
            case "min-y" -> {
                requireLength(args, 4, "用法: /rr region editor min-y <y>");
                editor.setMinY(player.getUniqueId(), finiteDouble(args[3], "min Y"));
                success(sender, "minY 已设置为 " + args[3]);
            }
            case "max-y" -> {
                requireLength(args, 4, "用法: /rr region editor max-y <y>");
                editor.setMaxY(player.getUniqueId(), finiteDouble(args[3], "max Y"));
                success(sender, "maxY 已设置为 " + args[3]);
            }
            default -> throw new IllegalArgumentException("未知 editor 操作");
        }
    }

    private void finish(Player player, String token){
        RegionEditSession session = editor.session(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException(
                        "你没有活动的编辑会话"
                ));
        RegionSaveRequest request = session.saveRequest(Optional.ofNullable(token));
        RegionMutationActor actor = mutationActor(player);
        infoLine(player, "正在异步校验并保存…");
        plugin.mutations().attemptSave(
                request, actor.subject()
        ).whenComplete((outcome, failure) -> onMain(() ->
                handleSaveOutcome(
                        player,
                        outcome,
                        failure
                )
        ));
    }

    private void music(CommandSender sender, String[] args){
        requirePermission(sender, CommandPermissions.MUSIC);
        if(args.length < 4){
            throw new IllegalArgumentException(
                    "用法: /rr music <region> <channel> <inherit|block|add|replace> ..."
            );
        }
        RegionRecord record = requireRecord(sender, args[1]);
        String channel = configuredChannel(args[2]);
        MusicPolicyMode mode;
        try {
            mode = MusicPolicyMode.valueOf(args[3].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception){
            throw new IllegalArgumentException(
                    "音乐策略必须是 inherit、block、add 或 replace"
            );
        }
        RegionMusicChannel previous = record.music().getChannel(channel);
        RegionMusicChannel.Builder builder = RegionMusicChannel.builder()
                .policy(mode)
                .order(previous == null ? 0 : previous.getOrder())
                .random(previous != null && previous.isRandom())
                .loop(previous == null || previous.isLoop())
                .volume(previous == null ? 1.0f : previous.getVolume())
                .pitch(previous == null ? 1.0f : previous.getPitch())
                .overwrite(previous == null || previous.isOverwrite());
        if(mode == MusicPolicyMode.ADD || mode == MusicPolicyMode.REPLACE){
            if(args.length < 7){
                throw new IllegalArgumentException(
                        "add/replace 用法: /rr music <region> <channel> <mode>"
                                + " <track-id> <sound> <duration-seconds> [order]"
                );
            }
            long duration;
            try {
                duration = Long.parseLong(args[6]);
            } catch (NumberFormatException exception){
                throw new IllegalArgumentException("duration 必须是正整数秒");
            }
            builder.tracks(List.of(new MusicTrack(args[4], args[5], duration)));
            if(args.length >= 8){
                try {
                    builder.order(Integer.parseInt(args[7]));
                } catch (NumberFormatException exception){
                    throw new IllegalArgumentException("order 必须是整数");
                }
            }
        }
        RegionMusicChannel changed = builder.build();
        administration.updateMusic(record.region().key(), profile -> {
            LinkedHashMap<String, RegionMusicChannel> channels =
                    new LinkedHashMap<>(profile.getChannels());
            channels.put(channel, changed);
            return new RegionMusicProfile(channels, profile.getBinding());
        }, mutationActor(sender)).whenComplete((result, failure) -> onMain(() ->
                handleAdministration(
                        sender, result, failure, "音乐策略已更新"
                )
        ));
        infoLine(sender, "正在异步保存音乐配置…");
    }

    private void reload(CommandSender sender){
        requirePermission(sender, CommandPermissions.RELOAD);
        plugin.reloadAll(sender);
    }

    private void module(CommandSender sender, String[] args){
        if(args.length < 4){
            throw new IllegalArgumentException(
                    "用法: /rr module <bind|unbind|info> <music|commands> <profile-region> ..."
            );
        }
        String operation = lower(args[1]);
        ModuleKind kind = moduleKind(args[2]);
        requirePermission(sender, modulePermission(kind));
        RegionRecord record = requireRecord(sender, args[3]);
        switch(operation){
            case "bind" -> bindModule(sender, args, kind, record);
            case "unbind" -> {
                requireLength(
                        args, 4,
                        "用法: /rr module unbind <music|commands> <profile-region>"
                );
                updateModuleBinding(
                        sender, kind, record, ModuleRegionBinding.nativeSelf(),
                        "模块已恢复绑定到自身原生区域"
                );
            }
            case "info" -> {
                requireLength(
                        args, 4,
                        "用法: /rr module info <music|commands> <profile-region>"
                );
                ModuleRegionBinding binding = moduleBinding(record, kind);
                ProviderRegionReference target = binding.resolve(record.region().key());
                boolean available = plugin.api().provider(target.providerId())
                        .map(RegionProvider::available)
                        .orElse(false);
                boolean resolves = plugin.api().moduleBindings().target(
                        plugin.api().snapshot(), kind, record.region().key()
                ).isPresent();
                infoLine(sender, kind.name().toLowerCase(Locale.ROOT)
                        + " profile=" + record.region().key()
                        + ", binding=" + binding
                        + ", providerAvailable=" + available
                        + ", targetResolves=" + resolves);
            }
            default -> throw new IllegalArgumentException(
                    "模块操作必须是 bind、unbind 或 info"
            );
        }
    }

    private void bindModule(CommandSender sender,
                            String[] args,
                            ModuleKind kind,
                            RegionRecord record){
        requireLength(
                args, 6,
                "用法: /rr module bind <music|commands> <profile-region>"
                        + " <provider> <provider-region>"
        );
        RegionProvider provider = plugin.api().provider(args[4])
                .orElseThrow(() -> new IllegalArgumentException(
                        "未知区域 Provider: " + args[4]
                ));
        if(!provider.available()){
            throw new IllegalStateException(
                    "区域 Provider 当前不可用: " + provider.id()
            );
        }
        ModuleRegionBinding binding = ModuleRegionBinding.toProvider(
                provider.id(), args[5]
        );
        if(plugin.api().moduleBindings().target(
                plugin.api().snapshot(), record.region().key(), binding
        ).isEmpty()){
            throw new IllegalArgumentException(
                    "Provider " + provider.id() + " 中不存在区域 " + args[5]
            );
        }
        updateModuleBinding(
                sender,
                kind,
                record,
                binding,
                "模块 Provider 绑定已更新"
        );
    }

    private void updateModuleBinding(CommandSender sender,
                                     ModuleKind kind,
                                     RegionRecord record,
                                     ModuleRegionBinding binding,
                                     String successMessage){
        RegionMutationActor actor = mutationActor(sender);
        java.util.concurrent.CompletionStage<AdministrationResult> stage =
                switch(kind){
                    case MUSIC -> administration.updateMusic(
                            record.region().key(),
                            profile -> profile.withBinding(binding),
                            actor
                    );
                    case COMMANDS -> administration.updateCommands(
                            record.region().key(),
                            profile -> profile.withBinding(binding),
                            actor
                    );
                };
        stage.whenComplete((result, failure) -> onMain(() ->
                handleAdministration(sender, result, failure, successMessage)
        ));
        infoLine(sender, "正在异步保存模块绑定…");
    }

    private ModuleRegionBinding moduleBinding(RegionRecord record,
                                              ModuleKind kind){
        return switch(kind){
            case MUSIC -> record.music().getBinding();
            case COMMANDS -> record.commands().getBinding();
        };
    }

    private ModuleKind moduleKind(String raw){
        return switch(lower(raw)){
            case "music" -> ModuleKind.MUSIC;
            case "commands" -> ModuleKind.COMMANDS;
            default -> throw new IllegalArgumentException(
                    "模块必须是 music 或 commands"
            );
        };
    }

    private String modulePermission(ModuleKind kind){
        return switch(kind){
            case MUSIC -> CommandPermissions.MUSIC;
            case COMMANDS -> CommandPermissions.COMMANDS;
        };
    }

    private void mutateCore(CommandSender sender,
                            RegionRecord previous,
                            UnaryOperator<Region> update,
                            String successMessage){
        administration.updateCore(
                previous.region().key(), update, mutationActor(sender)
        ).whenComplete((result, failure) -> onMain(() ->
                handleAdministration(sender, result, failure, successMessage)
        ));
        infoLine(sender, "正在异步保存…");
    }

    private void handleSaveOutcome(CommandSender sender,
                                   RegionSaveOutcome outcome,
                                   Throwable failure){
        if(failure != null){
            error(sender, "保存任务失败: " + safeMessage(failure));
            return;
        }
        if(outcome instanceof RegionSaveOutcome.Saved saved){
            success(sender, "区域已保存（" + saved.choice() + ", revision "
                    + saved.snapshot().revision() + "）");
            return;
        }
        if(outcome instanceof RegionSaveOutcome.ConfirmationRequired required){
            confirmation(sender, required);
            return;
        }
        sendNonSaved(sender, outcome);
    }

    private void handleGlobalSaveOutcome(CommandSender sender,
                                         RegionSaveOutcome outcome,
                                         Throwable failure){
        if(failure != null){
            error(sender, "global 区域保存失败: " + safeMessage(failure));
            return;
        }
        if(outcome instanceof RegionSaveOutcome.Saved saved){
            success(sender, "global 区域已覆盖当前整个世界（revision "
                    + saved.snapshot().revision() + "）");
            return;
        }
        sendNonSaved(sender, outcome);
    }

    private void confirmation(CommandSender sender,
                              RegionSaveOutcome.ConfirmationRequired required){
        sender.sendMessage(ChatColor.YELLOW
                + "检测到体积重叠；请选择一次性确认方案（30 秒有效）：");
        for(ConfirmationOption option : required.options()){
            String label = option.option().choice() == SaveChoice.SET_PARENT
                    ? "设为子区域 → " + option.option().parent()
                    .map(RegionKey::toString).orElse("?")
                    : "保留重叠";
            String command = "/rr region editor finish " + option.token();
            if(sender instanceof Player player){
                player.sendMessage(Component.text(
                                "[点击确认: " + label + "]",
                                NamedTextColor.AQUA
                        )
                        .clickEvent(ClickEvent.runCommand(command)));
            } else {
                infoLine(sender, label + ": " + command);
            }
        }
    }

    private void sendNonSaved(CommandSender sender, RegionSaveOutcome outcome){
        if(outcome instanceof RegionSaveOutcome.Rejected rejected){
            error(sender, "保存被拒绝 [" + rejected.reason() + "]: "
                    + rejected.message());
        } else if(outcome instanceof RegionSaveOutcome.Stale stale){
            sender.sendMessage(ChatColor.YELLOW + "编辑已过期 [" + stale.reason()
                    + "]: " + stale.message() + "；请 cancel 后重新编辑");
        } else if(outcome instanceof RegionSaveOutcome.Failed failed){
            error(sender, failed.message() + ": " + safeMessage(failed.cause()));
        } else if(outcome instanceof RegionSaveOutcome.ConfirmationRequired){
            error(sender, "该无会话修改意外需要确认，请使用区域编辑器");
        } else {
            error(sender, "未知保存结果");
        }
    }

    private void handleAdministration(CommandSender sender,
                                      AdministrationResult result,
                                      Throwable failure,
                                      String successMessage){
        if(failure != null){
            error(sender, "管理任务失败: " + safeMessage(failure));
            return;
        }
        if(result == null){
            error(sender, "管理任务未返回结果");
            return;
        }
        if(!result.saved()){
            error(sender, result.status() + ": " + result.message());
            return;
        }
        success(sender, successMessage + "（revision "
                + result.currentSnapshot().revision() + "）");
    }

    private RegionRecord requireRecord(CommandSender sender, String id){
        String normalized = RegionKey.normalizeId(
                lower(id).equals("global") ? RegionKey.GLOBAL_ID : id
        );
        RegionSnapshot snapshot = plugin.api().snapshot();
        if(sender instanceof Player player){
            RegionKey key = new RegionKey(BukkitWorlds.id(player.getWorld()), normalized);
            return Optional.ofNullable(snapshot.records().get(key)).orElseThrow(() ->
                    new IllegalArgumentException("当前世界不存在区域 " + normalized)
            );
        }
        List<RegionRecord> matches = snapshot.records().values().stream()
                .filter(record -> record.region().key().id().equals(normalized))
                .toList();
        if(matches.isEmpty()){
            throw new IllegalArgumentException("不存在区域 " + normalized);
        }
        if(matches.size() > 1){
            throw new IllegalArgumentException(
                    "多个世界存在区域 " + normalized + "；请由对应世界内玩家执行"
            );
        }
        return matches.getFirst();
    }

    private void requireCanEdit(CommandSender sender, Region region){
        if(sender.hasPermission(MutationPermissions.EDIT_ANY)){
            return;
        }
        if(sender instanceof Player player
                && sender.hasPermission(MutationPermissions.EDIT_OWN)){
            Subject subject = BukkitSubjects.from(player);
            if(plugin.api().snapshot().graph().hasInheritedOwner(
                    region.key(), player.getUniqueId(), subject.groups()
            )){
                return;
            }
        }
        throw new IllegalStateException("你没有该区域的编辑权限");
    }

    private RegionMutationActor mutationActor(CommandSender sender){
        if(sender instanceof Player player){
            io.github.rookiecuzz.rookieregions.rule.Subject subject =
                    BukkitSubjects.from(player);
            return new RegionMutationActor(
                    player.getUniqueId().toString(),
                    player.getUniqueId(),
                    subject.groups(),
                    subject.permissions()
            );
        }
        LinkedHashSet<String> permissions = effectivePermissions(sender);
        return new RegionMutationActor(
                "console", null, Set.of(), permissions
        );
    }

    private LinkedHashSet<String> effectivePermissions(CommandSender sender){
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for(String permission : List.of(
                MutationPermissions.ADMIN,
                MutationPermissions.CREATE,
                MutationPermissions.EDIT_OWN,
                MutationPermissions.EDIT_ANY,
                MutationPermissions.OVERLAP,
                CommandPermissions.VIEW,
                CommandPermissions.DELETE,
                CommandPermissions.FLAG,
                CommandPermissions.MUSIC,
                CommandPermissions.COMMANDS,
                CommandPermissions.RELOAD)){
            if(sender.hasPermission(permission)){
                result.add(permission);
            }
        }
        return result;
    }

    private Region copy(Region source,
                        int priority,
                        RegionDomain owners,
                        RegionDomain members,
                        Map<String, FlagValue<?>> flags){
        Region.Builder builder = Region.builder(source.key(), source.shape())
                .priority(priority)
                .owners(owners)
                .members(members);
        source.parent().ifPresent(builder::parent);
        flags.values().forEach(builder::flagValue);
        return builder.build();
    }

    private DomainEntry domainEntry(String raw){
        if(lower(raw).startsWith("group:")){
            String group = raw.substring("group:".length()).trim();
            if(group.isEmpty()){
                throw new IllegalArgumentException("group 名称不能为空");
            }
            return new DomainEntry(null, group);
        }
        Player online = Bukkit.getPlayerExact(raw);
        if(online != null){
            return new DomainEntry(online.getUniqueId(), null);
        }
        try {
            return new DomainEntry(UUID.fromString(raw), null);
        } catch (IllegalArgumentException ignored){
            throw new IllegalArgumentException(
                    "玩家不在线；请提供 UUID，或使用 group:<name>"
            );
        }
    }

    private RegionDomain changeDomain(RegionDomain source,
                                      DomainEntry entry,
                                      boolean add){
        LinkedHashSet<UUID> players = new LinkedHashSet<>(source.players());
        LinkedHashSet<String> groups = new LinkedHashSet<>(source.groups());
        if(entry.player() != null){
            if(add){
                players.add(entry.player());
            } else {
                players.remove(entry.player());
            }
        } else if(add){
            groups.add(entry.group());
        } else {
            groups.remove(entry.group().toLowerCase(Locale.ROOT));
        }
        return new RegionDomain(players, groups);
    }

    private FlagValue<?> decodeFlag(Flag<?> flag, String raw){
        Object encoded;
        if(flag.valueType() == Integer.class){
            try {
                encoded = Integer.parseInt(raw);
            } catch (NumberFormatException exception){
                throw new IllegalArgumentException("该 flag 需要整数");
            }
        } else if(flag.valueType() == Set.class){
            encoded = Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        } else {
            encoded = raw;
        }
        return decodeCaptured(flag, encoded);
    }

    private <T> FlagValue<T> decodeCaptured(Flag<T> flag, Object encoded){
        return flag.value(flag.codec().decode(encoded));
    }

    private String configuredChannel(String requested){
        return plugin.settings().musicChannels().keySet().stream()
                .filter(channel -> channel.equalsIgnoreCase(requested))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "未知音乐频道: " + requested
                ));
    }

    private String domainText(RegionDomain domain){
        return "players=" + domain.players() + ", groups=" + domain.groups();
    }

    private String shapeName(Region region){
        String simple = region.shape().getClass().getSimpleName();
        return simple.endsWith("Shape")
                ? simple.substring(0, simple.length() - "Shape".length())
                : simple;
    }

    private void help(CommandSender sender){
        sender.sendMessage(ChatColor.GOLD + "RookieRegions /rr");
        if(sender.hasPermission(CommandPermissions.VIEW)){
            infoLine(sender, "/rr region <info|list> ...");
        }
        if(sender.hasPermission(MutationPermissions.CREATE)
                || sender.hasPermission(MutationPermissions.EDIT_OWN)
                || sender.hasPermission(MutationPermissions.EDIT_ANY)){
            infoLine(sender, "/rr region <create|edit|editor> ...");
        }
        if(sender.hasPermission(CommandPermissions.MUSIC)){
            infoLine(sender, "/rr music <region> <channel> <policy> ...");
        }
        if(sender.hasPermission(CommandPermissions.MUSIC)
                || sender.hasPermission(CommandPermissions.COMMANDS)){
            infoLine(sender, "/rr module <bind|unbind|info> <music|commands> ...");
        }
        if(sender.hasPermission(CommandPermissions.RELOAD)){
            infoLine(sender, "/rr reload");
        }
    }

    private void regionHelp(CommandSender sender){
        infoLine(sender, "/rr region " + REGION_COMMANDS);
    }

    private Player requirePlayer(CommandSender sender){
        if(sender instanceof Player player){
            return player;
        }
        throw new IllegalStateException("该命令只能由玩家执行");
    }

    private void requirePermission(CommandSender sender, String permission){
        if(!sender.hasPermission(permission)){
            throw new IllegalStateException("缺少权限: " + permission);
        }
    }

    private void requireLength(String[] args, int length, String usage){
        if(args.length != length){
            throw new IllegalArgumentException(usage);
        }
    }

    private double finiteDouble(String raw, String label){
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException exception){
            throw new IllegalArgumentException(label + " 必须是数字");
        }
        if(!Double.isFinite(value)){
            throw new IllegalArgumentException(label + " 必须是有限数字");
        }
        return value;
    }

    private void onMain(Runnable task){
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private void success(CommandSender sender, String message){
        sender.sendMessage(ChatColor.GREEN + "[RookieRegions] " + message);
    }

    private void infoLine(CommandSender sender, String message){
        sender.sendMessage(ChatColor.GRAY + "[RookieRegions] " + message);
    }

    private void error(CommandSender sender, String message){
        sender.sendMessage(ChatColor.RED + "[RookieRegions] " + message);
    }

    private String safeMessage(Throwable failure){
        Throwable current = failure;
        while(current.getCause() != null && current.getCause() != current){
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private String lower(String value){
        return value.toLowerCase(Locale.ROOT);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {
        if(args.length == 1){
            return matches(args[0], availableRoots(sender));
        }
        if(args.length >= 2 && lower(args[0]).equals("region")){
            return regionTabs(sender, args);
        }
        if(args.length >= 2 && lower(args[0]).equals("music")){
            if(!sender.hasPermission(CommandPermissions.MUSIC)){
                return List.of();
            }
            if(args.length == 2){
                return matches(args[1], regionIds(sender));
            }
            if(args.length == 3){
                return matches(args[2], plugin.settings()
                        .musicChannels().keySet());
            }
            if(args.length == 4){
                return matches(args[3], MUSIC_POLICIES);
            }
        }
        if(args.length >= 2 && lower(args[0]).equals("module")){
            return moduleTabs(sender, args);
        }
        return List.of();
    }

    private List<String> moduleTabs(CommandSender sender, String[] args){
        if(args.length == 2){
            return matches(args[1], List.of("bind", "unbind", "info"));
        }
        if(args.length == 3){
            ArrayList<String> kinds = new ArrayList<>();
            if(sender.hasPermission(CommandPermissions.MUSIC)){
                kinds.add("music");
            }
            if(sender.hasPermission(CommandPermissions.COMMANDS)){
                kinds.add("commands");
            }
            return matches(args[2], kinds);
        }
        if(args.length == 4){
            return matches(args[3], regionIds(sender));
        }
        if(args.length == 5 && lower(args[1]).equals("bind")){
            return matches(args[4], plugin.api().providers().keySet());
        }
        if(args.length == 6 && lower(args[1]).equals("bind")
                && lower(args[4]).equals(NativeRegionProvider.ID)){
            return matches(args[5], regionIds(sender));
        }
        return List.of();
    }

    private List<String> regionTabs(CommandSender sender, String[] args){
        if(args.length == 2){
            return matches(args[1], availableRegionCommands(sender));
        }
        String operation = lower(args[1]);
        if(operation.equals("editor")){
            return args.length == 3
                    ? matches(args[2], EDITOR_COMMANDS)
                    : List.of();
        }
        if(operation.equals("create") && args.length == 4){
            return matches(
                    args[3], List.of("cuboid", "polygon", "sliced", "global")
            );
        }
        if(operation.equals("create") && args.length == 3){
            return matches(args[2], List.of("global"));
        }
        if(List.of("edit", "delete", "info", "priority", "parent",
                "owner", "member", "flag").contains(operation)
                && args.length == 3){
            return matches(args[2], regionIds(sender));
        }
        if(operation.equals("parent") && args.length == 4){
            ArrayList<String> values = new ArrayList<>(regionIds(sender));
            values.add("global");
            return matches(args[3], values);
        }
        if((operation.equals("owner") || operation.equals("member"))
                && args.length == 4){
            return matches(args[3], List.of("add", "remove"));
        }
        if(operation.equals("flag") && args.length == 4){
            return matches(args[3], plugin.api().flagRegistry().values()
                    .stream().map(Flag::name).toList());
        }
        if(operation.equals("flag") && args.length == 5){
            return matches(args[4], List.of("allow", "deny", "unset"));
        }
        return List.of();
    }

    private List<String> availableRoots(CommandSender sender){
        ArrayList<String> result = new ArrayList<>(List.of("help"));
        if(!availableRegionCommands(sender).isEmpty()){
            result.add("region");
        }
        if(sender.hasPermission(CommandPermissions.MUSIC)){
            result.add("music");
        }
        if(sender.hasPermission(CommandPermissions.MUSIC)
                || sender.hasPermission(CommandPermissions.COMMANDS)){
            result.add("module");
        }
        if(sender.hasPermission(CommandPermissions.RELOAD)){
            result.add("reload");
        }
        return result;
    }

    private List<String> availableRegionCommands(CommandSender sender){
        ArrayList<String> result = new ArrayList<>();
        if(sender.hasPermission(CommandPermissions.VIEW)){
            result.addAll(List.of("info", "list"));
        }
        if(sender.hasPermission(MutationPermissions.CREATE)){
            result.add("create");
        }
        if(sender.hasPermission(MutationPermissions.EDIT_OWN)
                || sender.hasPermission(MutationPermissions.EDIT_ANY)){
            result.addAll(List.of("edit", "priority", "owner", "member", "editor"));
        }
        if(sender.hasPermission(MutationPermissions.EDIT_ANY)){
            result.add("parent");
        }
        if(sender.hasPermission(CommandPermissions.DELETE)){
            result.add("delete");
        }
        if(sender.hasPermission(CommandPermissions.FLAG)){
            result.add("flag");
        }
        return result;
    }

    private List<String> regionIds(CommandSender sender){
        UUID world = sender instanceof Player player
                ? player.getWorld().getUID()
                : null;
        return plugin.api().snapshot().records().values().stream()
                .map(RegionRecord::region)
                .filter(region -> world == null
                        || region.key().world().uuid().equals(world))
                .map(region -> region.key().isGlobal()
                        ? "global"
                        : region.key().id())
                .distinct()
                .sorted()
                .toList();
    }

    public static List<String> matches(String prefix,
                                       Collection<String> candidates){
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT)
                        .startsWith(normalized))
                .distinct()
                .sorted()
                .toList();
    }

    static boolean isGlobalAlias(String value){
        if(value == null){
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("global")
                || normalized.equals(RegionKey.GLOBAL_ID);
    }

    private record DomainEntry(UUID player, String group) {
    }
}
