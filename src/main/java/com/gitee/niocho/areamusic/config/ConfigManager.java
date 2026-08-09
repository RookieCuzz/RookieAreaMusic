package com.gitee.niocho.areamusic.config;

import com.gitee.niocho.areamusic.RookieAreaMusic;
import com.gitee.niocho.areamusic.geometry.SlicedPolygonVolume;
import com.gitee.niocho.areamusic.source.SoundSource;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.bukkit.configuration.ConfigurationSection;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConfigManager {
    private static final String WORLDS_DIRECTORY = "worlds";
    private static final String REGIONS_DIRECTORY = "regions";
    private static final String SOURCES_DIRECTORY = "sources";
    private static final String AREA_FILE = "area.json";
    private static final String MUSIC_FILE = "music.json";
    private static final int CHECK_PERIOD_TICKS = 20;

    private final RookieAreaMusic plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private volatile Map<String, Map<String, AreaDto>> areas;
    private volatile Map<String, MusicDto> musics;
    private volatile Map<String, Map<String, SoundSource>> soundSources;
    private volatile PlaybackChannelRegistry channelRegistry =
            PlaybackChannelRegistry.defaults();

    public ConfigManager(RookieAreaMusic plugin) {
        this.plugin = plugin;
    }

    public void load() throws IOException {
        reload();
    }

    public void reload() throws IOException {
        this.plugin.saveDefaultConfig();
        this.plugin.reloadConfig();
        validateCheckPeriod();
        PlaybackChannelRegistry loadedChannels = loadPlaybackChannels();

        Path dataRoot = this.plugin.getDataFolder().toPath();
        Files.createDirectories(dataRoot);
        Map<String, Map<String, AreaDto>> loadedAreas = new ConcurrentHashMap<>();
        Map<String, MusicDto> loadedMusics = new ConcurrentHashMap<>();
        Map<String, Map<String, SoundSource>> loadedSources =
                new ConcurrentHashMap<>();
        loadRegionDirectories(
                dataRoot.resolve(WORLDS_DIRECTORY),
                loadedAreas,
                loadedMusics,
                loadedChannels
        );
        loadSoundSourceFiles(
                dataRoot.resolve(WORLDS_DIRECTORY),
                loadedSources
        );

        // 只有所有分区都成功读取后才替换当前运行配置。
        this.areas = loadedAreas;
        this.musics = loadedMusics;
        this.soundSources = loadedSources;
        this.channelRegistry = loadedChannels;
    }

    public void save() throws IOException {
        this.plugin.saveConfig();
        Path worldsRoot = this.plugin.getDataFolder().toPath().resolve(WORLDS_DIRECTORY);
        Files.createDirectories(worldsRoot);

        if(areas == null){
            return;
        }
        for(Map<String, AreaDto> worldAreas : areas.values()){
            if(worldAreas == null){
                continue;
            }
            for(AreaDto area : worldAreas.values()){
                writeRegionFiles(worldsRoot, area, musics);
            }
        }
        if(soundSources != null){
            for(Map<String, SoundSource> worldSources : soundSources.values()){
                if(worldSources == null){
                    continue;
                }
                for(SoundSource source : worldSources.values()){
                    writeSoundSourceFile(worldsRoot, source);
                }
            }
        }
    }

    public void deleteRegionFiles(String worldName, String areaId) throws IOException {
        Path worldsRoot = this.plugin.getDataFolder().toPath().resolve(WORLDS_DIRECTORY);
        Path regionDirectory = resolveRegionDirectory(worldsRoot, worldName, areaId);
        if(!Files.exists(regionDirectory)){
            return;
        }

        Path trashRoot = this.plugin.getDataFolder().toPath().resolve(".deleted-regions");
        Files.createDirectories(trashRoot);
        String backupName = System.currentTimeMillis() + "-" + safeBackupName(worldName)
                + "-" + safeBackupName(areaId);
        Path backup = trashRoot.resolve(backupName);
        try {
            Files.move(regionDirectory, backup, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored){
            Files.move(regionDirectory, backup);
        }
    }

    /**
     * Persists one editor result without rewriting unrelated regions. New regions are
     * staged and moved into place as a directory; edits atomically replace area.json
     * and deliberately leave music.json untouched.
     */
    public synchronized void upsertRegion(AreaDto area, boolean create) throws IOException {
        if(area == null){
            throw new IOException("区域不能为空");
        }
        SlicedPolygonVolume volume = validateRuntimeArea(area);
        area.setShape(volume);
        area.setMinPoint(volume.getMinPoint());
        area.setMaxPoint(volume.getMaxPoint());
        area.setChannel(normalizeChannel(area.getChannel()));
        area.setOrder(normalizeOrder(area.getOrder()));
        area.setUuid(createRegionUuid(area.getWorld(), area.getAreaId()));
        if(area.getMusicId() == null){
            area.setMusicId(new CopyOnWriteArrayList<>());
        }

        AreaDto existing = findArea(area.getWorld(), area.getAreaId());
        if(create && existing != null){
            throw new IOException("区域已经存在: " + area.getWorld() + "/" + area.getAreaId());
        }
        if(!create && existing == null){
            throw new IOException("区域不存在: " + area.getWorld() + "/" + area.getAreaId());
        }

        Path dataRoot = plugin.getDataFolder().toPath();
        Path worldsRoot = dataRoot.resolve(WORLDS_DIRECTORY);
        Path targetDirectory = resolveRegionDirectory(
                worldsRoot,
                area.getWorld(),
                area.getAreaId()
        );

        if(create){
            if(Files.exists(targetDirectory)){
                throw new IOException("区域目录已经存在: " + targetDirectory.toAbsolutePath());
            }
            Path stagingRoot = dataRoot.resolve(".editor-staging")
                    .resolve(UUID.randomUUID().toString());
            Path stagingWorlds = stagingRoot.resolve(WORLDS_DIRECTORY);
            try {
                writeRegionFiles(stagingWorlds, area, musics);
                Path stagedDirectory = resolveRegionDirectory(
                        stagingWorlds,
                        area.getWorld(),
                        area.getAreaId()
                );
                Files.createDirectories(targetDirectory.getParent());
                try {
                    Files.move(stagedDirectory, targetDirectory, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored){
                    Files.move(stagedDirectory, targetDirectory);
                }
            } finally {
                cleanupStaging(stagingRoot);
            }
        } else {
            writeJsonAtomically(
                    targetDirectory.resolve(AREA_FILE),
                    createAreaConfig(area, volume)
            );
        }

        areas.computeIfAbsent(area.getWorld(), ignored -> new ConcurrentHashMap<>())
                .put(area.getUuid(), area);
    }

    public void unload() throws IOException {
        this.save();
    }

    public int getCheckPeriod(){
        return CHECK_PERIOD_TICKS;
    }

    public Map<String, Map<String, AreaDto>> getAreas() {
        return areas;
    }

    public Map<String, MusicDto> getMusics() {
        return musics;
    }

    public Map<String, Map<String, SoundSource>> getSoundSources() {
        return soundSources;
    }

    public PlaybackChannelRegistry getChannelRegistry() {
        return channelRegistry;
    }

    public AreaDto findArea(String worldName, String areaId){
        Map<String, AreaDto> worldAreas = areas == null ? null : areas.get(worldName);
        if(worldAreas == null){
            return null;
        }
        for(AreaDto area : worldAreas.values()){
            if(areaId.equals(area.getAreaId())){
                return area;
            }
        }
        return null;
    }

    public static String createRegionUuid(String worldName, String areaId){
        return stableUuid("region", worldName, areaId);
    }

    public static String createTrackUuid(String worldName, String areaId, String musicId){
        return stableUuid("music", worldName, areaId, musicId);
    }

    public static String createSoundSourceUuid(String worldName,
                                               String sourceId){
        return stableUuid("source", worldName, sourceId);
    }

    private void loadRegionDirectories(Path worldsRoot,
                                       Map<String, Map<String, AreaDto>> loadedAreas,
                                       Map<String, MusicDto> loadedMusics,
                                       PlaybackChannelRegistry channels) throws IOException {
        if(!Files.exists(worldsRoot)){
            Files.createDirectories(worldsRoot);
            return;
        }

        for(Path worldDirectory : listDirectories(worldsRoot)){
            String worldName = worldDirectory.getFileName().toString();
            Path regionsRoot = worldDirectory.resolve(REGIONS_DIRECTORY);
            if(!Files.exists(regionsRoot)){
                continue;
            }
            for(Path regionDirectory : listDirectories(regionsRoot)){
                loadRegion(
                        worldName,
                        regionDirectory,
                        loadedAreas,
                        loadedMusics,
                        channels
                );
            }
        }
    }

    private void loadSoundSourceFiles(
            Path worldsRoot,
            Map<String, Map<String, SoundSource>> loadedSources)
            throws IOException {
        if(!Files.exists(worldsRoot)){
            Files.createDirectories(worldsRoot);
            return;
        }

        for(Path worldDirectory : listDirectories(worldsRoot)){
            String worldName = worldDirectory.getFileName().toString();
            Path sourcesRoot = worldDirectory.resolve(SOURCES_DIRECTORY);
            if(!Files.exists(sourcesRoot)){
                continue;
            }
            for(Path sourceFile : listJsonFiles(sourcesRoot)){
                String fileName = sourceFile.getFileName().toString();
                String sourceId = fileName.substring(
                        0,
                        fileName.length() - ".json".length()
                );
                validateSourceId(sourceId, sourceFile);
                SoundSourceConfig config = readJson(
                        sourceFile,
                        SoundSourceConfig.class,
                        null
                );
                SoundSource source = validateAndCreateSoundSource(
                        worldName,
                        sourceId,
                        config,
                        sourceFile
                );
                loadedSources.computeIfAbsent(
                        worldName,
                        ignored -> new ConcurrentHashMap<>()
                ).put(source.getUuid(), source);
            }
        }
    }

    private void loadRegion(String worldName,
                            Path regionDirectory,
                            Map<String, Map<String, AreaDto>> loadedAreas,
                            Map<String, MusicDto> loadedMusics,
                            PlaybackChannelRegistry channels) throws IOException {
        String areaId = regionDirectory.getFileName().toString();
        RegionAreaConfig areaConfig = readJson(
                regionDirectory.resolve(AREA_FILE),
                RegionAreaConfig.class,
                null
        );
        if(areaConfig == null){
            throw new IOException("缺少区域配置: " + regionDirectory.resolve(AREA_FILE));
        }
        normalizeAndValidatePlaybackSettings(
                areaConfig,
                channels,
                regionDirectory
        );
        SlicedPolygonVolume volume = validateAndCreateVolume(areaConfig, regionDirectory);

        Path musicPath = regionDirectory.resolve(MUSIC_FILE);
        RegionMusicConfig musicConfig;
        if(Files.exists(musicPath)){
            musicConfig = readJson(musicPath, RegionMusicConfig.class, new RegionMusicConfig());
        } else {
            musicConfig = new RegionMusicConfig();
            writeJsonAtomically(musicPath, musicConfig);
        }
        if(musicConfig.getMusic() == null){
            musicConfig.setMusic(new ArrayList<>());
        }

        List<String> trackUuids = new ArrayList<>();
        Set<String> localMusicIds = new HashSet<>();
        for(RegionMusicConfig.Track track : musicConfig.getMusic()){
            validateTrack(track, musicPath);
            if(!localMusicIds.add(track.getId())){
                throw new IOException("区域内存在重复音乐 ID " + track.getId() + ": " + musicPath);
            }
            String trackUuid = createTrackUuid(worldName, areaId, track.getId());
            if(loadedMusics.put(trackUuid, MusicDto.builder()
                    .uuid(trackUuid)
                    .musicId(track.getId())
                    .musicURL(track.getSound())
                    .musicDuration(track.getDuration())
                    .build()) != null){
                throw new IOException("音乐内部 ID 冲突: " + trackUuid);
            }
            trackUuids.add(trackUuid);
        }

        String areaUuid = createRegionUuid(worldName, areaId);
        AreaDto area = AreaDto.builder()
                .world(worldName)
                .uuid(areaUuid)
                .areaId(areaId)
                .musicId(new CopyOnWriteArrayList<>(trackUuids))
                .channel(areaConfig.getChannel())
                .order(areaConfig.getOrder())
                .priority(areaConfig.getPriority())
                .random(areaConfig.getRandom())
                .loop(areaConfig.getLoop())
                .enabled(areaConfig.getEnabled())
                .overWrite(areaConfig.getOverWrite())
                .volume(areaConfig.getVolume())
                .pitch(areaConfig.getPitch())
                .shape(volume)
                .minPoint(volume.getMinPoint())
                .maxPoint(volume.getMaxPoint())
                .build();
        loadedAreas.computeIfAbsent(
                worldName,
                ignored -> new ConcurrentHashMap<>()
        ).put(areaUuid, area);
    }

    private void writeRegionFiles(Path worldsRoot,
                                  AreaDto area,
                                  Map<String, MusicDto> availableMusics) throws IOException {
        SlicedPolygonVolume volume = validateRuntimeArea(area);
        area.setShape(volume);
        area.setMinPoint(volume.getMinPoint());
        area.setMaxPoint(volume.getMaxPoint());
        area.setChannel(normalizeChannel(area.getChannel()));
        area.setOrder(normalizeOrder(area.getOrder()));
        Path regionDirectory = resolveRegionDirectory(worldsRoot, area.getWorld(), area.getAreaId());
        Files.createDirectories(regionDirectory);

        RegionAreaConfig areaConfig = createAreaConfig(area, volume);

        List<RegionMusicConfig.Track> tracks = new ArrayList<>();
        if(area.getMusicId() != null){
            for(String musicUuid : area.getMusicId()){
                MusicDto music = availableMusics == null ? null : availableMusics.get(musicUuid);
                if(music == null){
                    throw new IOException("区域 " + area.getAreaId() + " 引用了不存在的音乐: " + musicUuid);
                }
                tracks.add(RegionMusicConfig.Track.builder()
                        .id(music.getMusicId())
                        .sound(music.getMusicURL())
                        .duration(music.getMusicDuration())
                        .build());
            }
        }

        writeJsonAtomically(regionDirectory.resolve(AREA_FILE), areaConfig);
        writeJsonAtomically(
                regionDirectory.resolve(MUSIC_FILE),
                RegionMusicConfig.builder().music(tracks).build()
        );
    }

    private RegionAreaConfig createAreaConfig(AreaDto area,
                                               SlicedPolygonVolume volume){
        return RegionAreaConfig.builder()
                .channel(area.getChannel())
                .order(area.getOrder())
                .priority(area.getPriority())
                .random(area.getRandom())
                .loop(area.getLoop())
                .enabled(area.getEnabled())
                .overWrite(area.getOverWrite())
                .volume(area.getVolume())
                .pitch(area.getPitch())
                .shape(volume.getConfig())
                .build();
    }

    private void writeSoundSourceFile(Path worldsRoot,
                                      SoundSource source) throws IOException {
        if(source == null){
            return;
        }
        validateSourceId(
                source.getSourceId(),
                worldsRoot.resolve(source.getSourceId() + ".json")
        );
        Path normalizedRoot = worldsRoot.toAbsolutePath().normalize();
        Path worldDirectory = resolveSingleSegment(
                normalizedRoot,
                source.getWorldName(),
                "世界"
        );
        Path sourcesRoot = worldDirectory.resolve(SOURCES_DIRECTORY).normalize();
        Files.createDirectories(sourcesRoot);
        Path sourceFile = sourcesRoot.resolve(
                source.getSourceId() + ".json"
        ).normalize();
        if(!sourceFile.getParent().equals(sourcesRoot)){
            throw new IOException("音源 ID 不能包含路径分隔符: "
                    + source.getSourceId());
        }

        SoundSourceConfig config = SoundSourceConfig.builder()
                .position(SoundSourceConfig.Position.builder()
                        .x(source.getX())
                        .y(source.getY())
                        .z(source.getZ())
                        .build())
                .sound(source.getSoundKey())
                .duration(source.getDurationSeconds())
                .interval(source.getIntervalSeconds())
                .volume(source.getVolume())
                .pitch(source.getPitch())
                .enabled(source.isEnabled())
                .build();
        validateAndCreateSoundSource(
                source.getWorldName(),
                source.getSourceId(),
                config,
                sourceFile
        );
        writeJsonAtomically(sourceFile, config);
    }

    private Path resolveRegionDirectory(Path worldsRoot,
                                        String worldName,
                                        String areaId) throws IOException {
        Path normalizedRoot = worldsRoot.toAbsolutePath().normalize();
        Path worldDirectory = resolveSingleSegment(normalizedRoot, worldName, "世界");
        Path regionsRoot = worldDirectory.resolve(REGIONS_DIRECTORY).normalize();
        return resolveSingleSegment(regionsRoot, areaId, "区域");
    }

    private Path resolveSingleSegment(Path parent, String value, String label) throws IOException {
        if(isBlank(value) || ".".equals(value) || "..".equals(value)){
            throw new IOException(label + "名称无效: " + value);
        }
        Path resolved = parent.resolve(value).toAbsolutePath().normalize();
        if(resolved.getParent() == null || !resolved.getParent().equals(parent.toAbsolutePath().normalize())){
            throw new IOException(label + "名称不能包含路径分隔符: " + value);
        }
        return resolved;
    }

    private void validateCheckPeriod() throws IOException {
        int configured = this.plugin.getConfig().getInt(
                "engine.checkPeriod",
                CHECK_PERIOD_TICKS
        );
        if(configured != CHECK_PERIOD_TICKS){
            this.plugin.getConfig().set("engine.checkPeriod", CHECK_PERIOD_TICKS);
            this.plugin.saveConfig();
            this.plugin.getLogger().info(
                    "异步区域音乐检查周期已固定为 20 ticks（1 秒）"
            );
        }
    }

    private PlaybackChannelRegistry loadPlaybackChannels() throws IOException {
        PlaybackChannelRegistry defaults = PlaybackChannelRegistry.defaults();
        boolean changed = false;
        ConfigurationSection root =
                this.plugin.getConfig().getConfigurationSection("channels");
        if(root == null){
            for(Map.Entry<String, PlaybackChannelConfig> entry
                    : defaults.asMap().entrySet()){
                writeChannelDefaults(entry.getKey(), entry.getValue());
            }
            changed = true;
        } else {
            for(Map.Entry<String, PlaybackChannelConfig> entry
                    : defaults.asMap().entrySet()){
                if(!root.isConfigurationSection(entry.getKey())){
                    writeChannelDefaults(entry.getKey(), entry.getValue());
                    changed = true;
                }
            }
        }
        root = this.plugin.getConfig().getConfigurationSection("channels");

        Map<String, PlaybackChannelConfig> loaded =
                new java.util.LinkedHashMap<>();
        try {
            if(root == null){
                throw new IllegalArgumentException("channels 配置不存在");
            }
            for(String channelName : root.getKeys(false)){
                ConfigurationSection channel =
                        root.getConfigurationSection(channelName);
                if(channel == null){
                    throw new IllegalArgumentException(
                            "频道配置必须是对象: " + channelName
                    );
                }
                loaded.put(channelName, PlaybackChannelConfig.builder()
                        .mode(ChannelMode.parse(channel.getString("mode")))
                        .maxLayers(channel.getInt("maxLayers"))
                        .trigger(ChannelTrigger.parse(
                                channel.getString("trigger")
                        ))
                        .build());
            }
            PlaybackChannelRegistry result =
                    PlaybackChannelRegistry.of(loaded);
            if(changed){
                this.plugin.saveConfig();
            }
            return result;
        } catch (IllegalArgumentException e){
            throw new IOException("播放频道配置无效: " + e.getMessage(), e);
        }
    }

    private void writeChannelDefaults(String name,
                                      PlaybackChannelConfig config){
        String path = "channels." + name + ".";
        this.plugin.getConfig().set(
                path + "mode",
                config.getMode().name().toLowerCase(java.util.Locale.ROOT)
        );
        this.plugin.getConfig().set(path + "maxLayers", config.getMaxLayers());
        this.plugin.getConfig().set(
                path + "trigger",
                config.getTrigger().name().toLowerCase(java.util.Locale.ROOT)
        );
    }

    private void normalizeAndValidatePlaybackSettings(
            RegionAreaConfig area,
            PlaybackChannelRegistry channels,
            Path regionDirectory) throws IOException {
        area.setChannel(normalizeChannel(area.getChannel()));
        area.setOrder(normalizeOrder(area.getOrder()));
        try {
            channels.require(area.getChannel());
        } catch (IllegalArgumentException e){
            throw new IOException(
                    "区域引用了未知频道 "
                            + area.getChannel()
                            + ": "
                            + regionDirectory.resolve(AREA_FILE),
                    e
            );
        }
    }

    private String normalizeChannel(String channel){
        if(channel == null || channel.trim().isEmpty()){
            return PlaybackChannelRegistry.DEFAULT_CHANNEL;
        }
        return channel.trim();
    }

    private int normalizeOrder(Integer order){
        return order == null ? 0 : order;
    }

    private SlicedPolygonVolume validateAndCreateVolume(RegionAreaConfig area,
                                                        Path regionDirectory) throws IOException {
        if(area.getPriority() == null
                || area.getRandom() == null
                || area.getLoop() == null
                || area.getEnabled() == null
                || area.getOverWrite() == null
                || !isValidVolume(area.getVolume())
                || !isValidPitch(area.getPitch())){
            throw new IOException("区域配置无效: " + regionDirectory.resolve(AREA_FILE));
        }

        try {
            if(area.getShape() != null){
                return new SlicedPolygonVolume(area.getShape());
            }
        } catch (IllegalArgumentException e){
            throw new IOException("区域切片结构无效: " + regionDirectory.resolve(AREA_FILE)
                    + " (" + e.getMessage() + ")", e);
        }
        throw new IOException("区域缺少 shape: " + regionDirectory.resolve(AREA_FILE));
    }

    private SlicedPolygonVolume validateRuntimeArea(AreaDto area) throws IOException {
        if(area == null || isBlank(area.getWorld()) || isBlank(area.getAreaId())){
            throw new IOException("区域缺少世界或区域名称");
        }
        RegionAreaConfig config = RegionAreaConfig.builder()
                .channel(normalizeChannel(area.getChannel()))
                .order(normalizeOrder(area.getOrder()))
                .priority(area.getPriority())
                .random(area.getRandom())
                .loop(area.getLoop())
                .enabled(area.getEnabled())
                .overWrite(area.getOverWrite())
                .volume(area.getVolume())
                .pitch(area.getPitch())
                .shape(area.getShape() == null ? null : area.getShape().getConfig())
                .build();
        normalizeAndValidatePlaybackSettings(
                config,
                channelRegistry,
                this.plugin.getDataFolder().toPath().resolve("runtime-area")
        );
        return validateAndCreateVolume(
                config,
                this.plugin.getDataFolder().toPath().resolve("runtime-area")
        );
    }

    private void validateTrack(RegionMusicConfig.Track track, Path musicPath) throws IOException {
        if(track == null
                || isBlank(track.getId())
                || isBlank(track.getSound())
                || track.getDuration() == null
                || track.getDuration() <= 0){
            throw new IOException("音乐配置无效: " + musicPath);
        }
    }

    private SoundSource validateAndCreateSoundSource(
            String worldName,
            String sourceId,
            SoundSourceConfig config,
            Path sourceFile) throws IOException {
        if(config == null){
            throw new IOException("音源配置为空: " + sourceFile.toAbsolutePath());
        }
        if(config.getEnabled() == null){
            config.setEnabled(true);
        }
        if(config.getInterval() == null){
            config.setInterval(0L);
        }
        if(config.getVolume() == null){
            config.setVolume(1.0f);
        }
        if(config.getPitch() == null){
            config.setPitch(1.0f);
        }

        SoundSourceConfig.Position position = config.getPosition();
        if(isBlank(worldName)
                || position == null
                || !isFinite(position.getX())
                || !isFinite(position.getY())
                || !isFinite(position.getZ())
                || isBlank(config.getSound())
                || config.getDuration() == null
                || config.getDuration() <= 0
                || config.getInterval() < 0
                || !isValidSourceVolume(config.getVolume())
                || !isValidPitch(config.getPitch())){
            throw new IOException("音源配置无效: " + sourceFile.toAbsolutePath());
        }

        return new SoundSource(
                createSoundSourceUuid(worldName, sourceId),
                worldName,
                sourceId,
                position.getX(),
                position.getY(),
                position.getZ(),
                config.getSound().trim(),
                config.getDuration(),
                config.getInterval(),
                config.getVolume(),
                config.getPitch(),
                config.getEnabled()
        );
    }

    private void validateSourceId(String sourceId,
                                  Path sourceFile) throws IOException {
        if(isBlank(sourceId)
                || ".".equals(sourceId)
                || "..".equals(sourceId)
                || !sourceId.matches("[A-Za-z0-9._-]+")){
            throw new IOException("音源文件名无效: " + sourceFile.toAbsolutePath());
        }
    }

    private boolean isValidVolume(Float volume){
        return volume != null
                && !volume.isNaN()
                && !volume.isInfinite()
                && volume >= 0.0f
                && volume <= 1.0f;
    }

    private boolean isValidSourceVolume(Float volume){
        return volume != null
                && !volume.isNaN()
                && !volume.isInfinite()
                && volume > 0.0f
                && volume <= 16.0f;
    }

    private boolean isValidPitch(Float pitch){
        return pitch != null
                && !pitch.isNaN()
                && !pitch.isInfinite()
                && pitch > 0.0f
                && pitch <= 2.0f;
    }

    private boolean isFinite(Double value){
        return value != null && !value.isNaN() && !value.isInfinite();
    }

    private <T> T readJson(Path file, Class<T> type, T defaultValue) throws IOException {
        if(!Files.exists(file)){
            return defaultValue;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            T result = gson.fromJson(reader, type);
            return result == null ? defaultValue : result;
        } catch (JsonParseException | IllegalStateException e){
            throw new IOException("JSON 配置格式错误: " + file.toAbsolutePath(), e);
        }
    }

    private void writeJsonAtomically(Path file, Object value) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(value, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void deleteTreeIfExists(Path root) throws IOException {
        if(root == null || !Files.exists(root)){
            return;
        }
        try (Stream<Path> paths = Files.walk(root)){
            List<Path> ordered = paths.sorted(java.util.Comparator.reverseOrder())
                    .collect(Collectors.toList());
            IOException failure = null;
            for(Path path : ordered){
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e){
                    if(failure == null){
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if(failure != null){
                throw failure;
            }
        }
    }

    private void cleanupStaging(Path root){
        try {
            deleteTreeIfExists(root);
        } catch (IOException e){
            plugin.getLogger().log(
                    Level.WARNING,
                    "无法清理区域编辑临时目录: " + root.toAbsolutePath(),
                    e
            );
        }
    }

    private List<Path> listDirectories(Path parent) throws IOException {
        try (Stream<Path> paths = Files.list(parent)){
            return paths.filter(Files::isDirectory)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private List<Path> listJsonFiles(Path parent) throws IOException {
        try (Stream<Path> paths = Files.list(parent)){
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static String stableUuid(String namespace, String... parts){
        StringBuilder source = new StringBuilder(namespace);
        for(String part : parts){
            source.append('\u0000').append(part);
        }
        return UUID.nameUUIDFromBytes(source.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String safeBackupName(String value){
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private boolean isBlank(String value){
        return value == null || value.trim().isEmpty();
    }
}
