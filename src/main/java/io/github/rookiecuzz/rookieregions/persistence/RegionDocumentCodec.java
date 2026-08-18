package io.github.rookiecuzz.rookieregions.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionDomain;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.core.shape.RegionShape;
import io.github.rookiecuzz.rookieregions.module.commands.RegionCommandProfile;
import io.github.rookiecuzz.rookieregions.module.music.MusicPolicyMode;
import io.github.rookiecuzz.rookieregions.module.music.MusicTrack;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicChannel;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicProfile;
import io.github.rookiecuzz.rookieregions.persistence.codec.DocumentFormatException;
import io.github.rookiecuzz.rookieregions.persistence.codec.ShapeJsonCodec;
import io.github.rookiecuzz.rookieregions.persistence.json.StrictJson;
import io.github.rookiecuzz.rookieregions.rule.Flag;
import io.github.rookiecuzz.rookieregions.rule.FlagRegistry;
import io.github.rookiecuzz.rookieregions.rule.FlagValue;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;
import io.github.rookiecuzz.rookieregions.runtime.ModuleRegionBinding;
import io.github.rookiecuzz.rookieregions.runtime.ProviderRegionReference;

import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict codec for one complete, atomic region document. */
public final class RegionDocumentCodec {
    public static final int SCHEMA_VERSION = 1;

    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersion", "id", "world", "parent", "priority", "shape",
            "owners", "members", "flags", "modules"
    );
    private static final Set<String> WORLD_FIELDS = Set.of("uuid", "key");
    private static final Set<String> DOMAIN_FIELDS = Set.of("players", "groups");
    private static final Set<String> MODULE_FIELDS = Set.of("music", "commands");
    private static final Set<String> MUSIC_FIELDS = Set.of("binding", "channels");
    private static final Set<String> MUSIC_CHANNEL_FIELDS = Set.of(
            "policy", "order", "random", "loop", "volume", "pitch",
            "overwrite", "tracks"
    );
    private static final Set<String> TRACK_FIELDS = Set.of(
            "id", "sound", "duration"
    );
    private static final Set<String> COMMAND_FIELDS = Set.of(
            "binding", "enter", "leave"
    );
    private static final Set<String> BINDING_FIELDS = Set.of("provider", "region");

    private final FlagRegistry flags;
    private final ShapeJsonCodec shapes;

    public RegionDocumentCodec(){
        this(ProtectionFlags.REGISTRY, ShapeJsonCodec.INSTANCE);
    }

    public RegionDocumentCodec(FlagRegistry flags, ShapeJsonCodec shapes) {
        if(flags == null || shapes == null){
            throw new IllegalArgumentException("flag registry and shape codec cannot be null");
        }
        this.flags = flags;
        this.shapes = shapes;
    }

    public RegionRecord decode(String json){
        try {
            return decode(StrictJson.parse(json));
        } catch(StrictJson.StrictJsonException exception){
            throw strictError(exception);
        }
    }

    public RegionRecord decode(Reader reader){
        try {
            return decode(StrictJson.parse(reader));
        } catch(StrictJson.StrictJsonException exception){
            throw strictError(exception);
        }
    }

    public RegionRecord decode(JsonElement element){
        JsonObject root = requireObject(element, "");
        rejectUnknownFields(root, ROOT_FIELDS, "");
        int schemaVersion = requireInt(
                requireField(root, "schemaVersion", ""),
                "/schemaVersion"
        );
        if(schemaVersion != SCHEMA_VERSION){
            throw error(
                    "/schemaVersion",
                    "unsupported schema version " + schemaVersion
                            + " (expected " + SCHEMA_VERSION + ")"
            );
        }

        String id = requireCanonicalRegionId(
                requireField(root, "id", ""), "/id"
        );
        WorldId world = decodeWorld(requireField(root, "world", ""));
        RegionKey key;
        try {
            key = new RegionKey(world, id);
        } catch(IllegalArgumentException exception){
            throw new DocumentFormatException("/id", exception.getMessage(), exception);
        }

        RegionKey parent = decodeParent(requireField(root, "parent", ""), key);
        int priority = requireInt(requireField(root, "priority", ""), "/priority");
        RegionShape shape = decodeShape(requireField(root, "shape", ""));
        RegionDomain owners = decodeDomain(requireField(root, "owners", ""), "/owners");
        RegionDomain members = decodeDomain(requireField(root, "members", ""), "/members");

        Region.Builder builder = Region.builder(key, shape)
                .priority(priority)
                .owners(owners)
                .members(members);
        if(parent != null){
            builder.parent(parent);
        }
        decodeFlags(requireField(root, "flags", ""), builder);

        Region region;
        try {
            region = builder.build();
        } catch(IllegalArgumentException | NullPointerException exception){
            throw new DocumentFormatException("", exception.getMessage(), exception);
        }
        validateRegionInvariants(region);
        validateFlagScopes(region);

        Modules modules = decodeModules(requireField(root, "modules", ""));
        return new RegionRecord(region, modules.music(), modules.commands());
    }

    public JsonObject encode(RegionRecord record){
        if(record == null){
            throw new IllegalArgumentException("region record cannot be null");
        }
        Region region = record.region();
        validateRegionInvariants(region);
        validateFlagScopes(region);

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("id", region.key().id());
        root.add("world", encodeWorld(region.key().world()));
        root.add(
                "parent",
                region.parent()
                        .<JsonElement>map(parent -> new JsonPrimitive(parent.id()))
                        .orElse(JsonNull.INSTANCE)
        );
        root.addProperty("priority", region.priority());
        root.add("shape", shapes.encode(region.shape()));
        root.add("owners", encodeDomain(region.owners()));
        root.add("members", encodeDomain(region.members()));
        root.add("flags", encodeFlags(region));
        root.add("modules", encodeModules(record));
        return root;
    }

    public String encodeToString(RegionRecord record){
        return StrictJson.write(encode(record));
    }

    private WorldId decodeWorld(JsonElement element){
        JsonObject object = requireObject(element, "/world");
        rejectUnknownFields(object, WORLD_FIELDS, "/world");
        String uuidText = requireNonBlankString(
                requireField(object, "uuid", "/world"),
                "/world/uuid"
        );
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidText);
        } catch(IllegalArgumentException exception){
            throw new DocumentFormatException(
                    "/world/uuid", "invalid world UUID", exception
            );
        }
        String key = requireNonBlankString(
                requireField(object, "key", "/world"),
                "/world/key"
        );
        try {
            return new WorldId(uuid, key);
        } catch(IllegalArgumentException exception){
            throw new DocumentFormatException("/world/key", exception.getMessage(), exception);
        }
    }

    private RegionKey decodeParent(JsonElement element, RegionKey child){
        if(element == null || element.isJsonNull()){
            return null;
        }
        String parentId = requireCanonicalRegionId(element, "/parent");
        try {
            return new RegionKey(child.world(), parentId);
        } catch(IllegalArgumentException exception){
            throw new DocumentFormatException("/parent", exception.getMessage(), exception);
        }
    }

    private RegionShape decodeShape(JsonElement element){
        try {
            return shapes.decode(element);
        } catch(DocumentFormatException exception){
            throw rebase("/shape", exception);
        }
    }

    private RegionDomain decodeDomain(JsonElement element, String pointer){
        JsonObject object = requireObject(element, pointer);
        rejectUnknownFields(object, DOMAIN_FIELDS, pointer);
        JsonArray players = requireArray(
                requireField(object, "players", pointer),
                pointer + "/players"
        );
        JsonArray groups = requireArray(
                requireField(object, "groups", pointer),
                pointer + "/groups"
        );
        RegionDomain.Builder builder = RegionDomain.builder();
        for(int index = 0; index < players.size(); index++){
            String itemPointer = pointer + "/players/" + index;
            String value = requireNonBlankString(players.get(index), itemPointer);
            try {
                builder.player(UUID.fromString(value));
            } catch(IllegalArgumentException exception){
                throw new DocumentFormatException(itemPointer, "invalid player UUID", exception);
            }
        }
        for(int index = 0; index < groups.size(); index++){
            String itemPointer = pointer + "/groups/" + index;
            String value = requireNonBlankString(groups.get(index), itemPointer);
            try {
                builder.group(value);
            } catch(IllegalArgumentException exception){
                throw new DocumentFormatException(itemPointer, exception.getMessage(), exception);
            }
        }
        return builder.build();
    }

    private void decodeFlags(JsonElement element, Region.Builder builder){
        JsonObject object = requireObject(element, "/flags");
        for(Map.Entry<String, JsonElement> entry : object.entrySet()){
            String pointer = "/flags/" + escape(entry.getKey());
            Flag<?> flag = flags.find(entry.getKey()).orElseThrow(() ->
                    error(pointer, "unknown flag '" + entry.getKey() + "'"));
            try {
                builder.flagValue(decodeFlag(flag, jsonToPlain(entry.getValue())));
            } catch(IllegalArgumentException exception){
                throw new DocumentFormatException(pointer, exception.getMessage(), exception);
            }
        }
    }

    private Modules decodeModules(JsonElement element){
        JsonObject object = requireObject(element, "/modules");
        rejectUnknownFields(object, MODULE_FIELDS, "/modules");
        RegionMusicProfile music = decodeMusic(
                requireField(object, "music", "/modules")
        );
        RegionCommandProfile commands = decodeCommands(
                requireField(object, "commands", "/modules")
        );
        return new Modules(music, commands);
    }

    private RegionMusicProfile decodeMusic(JsonElement element){
        JsonObject object = requireObject(element, "/modules/music");
        rejectUnknownFields(object, MUSIC_FIELDS, "/modules/music");
        JsonObject channels = requireObject(
                requireField(object, "channels", "/modules/music"),
                "/modules/music/channels"
        );
        LinkedHashMap<String, RegionMusicChannel> result = new LinkedHashMap<>();
        for(Map.Entry<String, JsonElement> entry : channels.entrySet()){
            String pointer = "/modules/music/channels/" + escape(entry.getKey());
            if(entry.getKey().trim().isEmpty()){
                throw error(pointer, "music channel name must not be blank");
            }
            if(result.containsKey(entry.getKey().trim())){
                throw error(pointer, "duplicate normalized music channel");
            }
            result.put(entry.getKey().trim(), decodeMusicChannel(entry.getValue(), pointer));
        }
        ModuleRegionBinding binding = decodeBinding(
                requireField(object, "binding", "/modules/music"),
                "/modules/music/binding"
        );
        try {
            return new RegionMusicProfile(result, binding);
        } catch(IllegalArgumentException exception){
            throw new DocumentFormatException("/modules/music/channels", exception.getMessage(), exception);
        }
    }

    private RegionMusicChannel decodeMusicChannel(JsonElement element, String pointer){
        JsonObject object = requireObject(element, pointer);
        rejectUnknownFields(object, MUSIC_CHANNEL_FIELDS, pointer);
        String policyText = requireNonBlankString(
                requireField(object, "policy", pointer), pointer + "/policy"
        );
        MusicPolicyMode policy;
        try {
            policy = MusicPolicyMode.parse(policyText);
        } catch(IllegalArgumentException exception){
            throw new DocumentFormatException(pointer + "/policy", exception.getMessage(), exception);
        }
        List<MusicTrack> tracks = decodeTracks(
                requireField(object, "tracks", pointer),
                pointer + "/tracks"
        );
        try {
            return RegionMusicChannel.builder()
                    .policy(policy)
                    .order(requireInt(requireField(object, "order", pointer), pointer + "/order"))
                    .random(requireBoolean(
                            requireField(object, "random", pointer), pointer + "/random"
                    ))
                    .loop(requireBoolean(
                            requireField(object, "loop", pointer), pointer + "/loop"
                    ))
                    .volume(requireFloat(
                            requireField(object, "volume", pointer), pointer + "/volume"
                    ))
                    .pitch(requireFloat(
                            requireField(object, "pitch", pointer), pointer + "/pitch"
                    ))
                    .overwrite(requireBoolean(
                            requireField(object, "overwrite", pointer), pointer + "/overwrite"
                    ))
                    .tracks(tracks)
                    .build();
        } catch(DocumentFormatException exception){
            throw exception;
        } catch(IllegalArgumentException exception){
            throw new DocumentFormatException(pointer, exception.getMessage(), exception);
        }
    }

    private List<MusicTrack> decodeTracks(JsonElement element, String pointer){
        JsonArray array = requireArray(element, pointer);
        ArrayList<MusicTrack> result = new ArrayList<>();
        for(int index = 0; index < array.size(); index++){
            String itemPointer = pointer + "/" + index;
            JsonObject object = requireObject(array.get(index), itemPointer);
            rejectUnknownFields(object, TRACK_FIELDS, itemPointer);
            String id = requireNonBlankString(
                    requireField(object, "id", itemPointer), itemPointer + "/id"
            );
            String sound = requireNonBlankString(
                    requireField(object, "sound", itemPointer), itemPointer + "/sound"
            );
            long duration = requireLong(
                    requireField(object, "duration", itemPointer),
                    itemPointer + "/duration"
            );
            try {
                result.add(new MusicTrack(id, sound, duration));
            } catch(IllegalArgumentException exception){
                throw new DocumentFormatException(itemPointer, exception.getMessage(), exception);
            }
        }
        return result;
    }

    private RegionCommandProfile decodeCommands(JsonElement element){
        JsonObject object = requireObject(element, "/modules/commands");
        rejectUnknownFields(object, COMMAND_FIELDS, "/modules/commands");
        List<String> enter = decodeStringList(
                requireField(object, "enter", "/modules/commands"),
                "/modules/commands/enter"
        );
        List<String> leave = decodeStringList(
                requireField(object, "leave", "/modules/commands"),
                "/modules/commands/leave"
        );
        ModuleRegionBinding binding = decodeBinding(
                requireField(object, "binding", "/modules/commands"),
                "/modules/commands/binding"
        );
        try {
            return new RegionCommandProfile(enter, leave, binding);
        } catch(IllegalArgumentException exception){
            throw new DocumentFormatException("/modules/commands", exception.getMessage(), exception);
        }
    }

    private static List<String> decodeStringList(JsonElement element, String pointer){
        JsonArray array = requireArray(element, pointer);
        ArrayList<String> result = new ArrayList<>();
        for(int index = 0; index < array.size(); index++){
            result.add(requireNonBlankString(array.get(index), pointer + "/" + index));
        }
        return result;
    }

    private JsonObject encodeFlags(Region region){
        JsonObject result = new JsonObject();
        region.flags().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String pointer = "/flags/" + escape(entry.getKey());
                    Flag<?> registered = flags.find(entry.getKey()).orElseThrow(() ->
                            error(pointer, "unknown flag '" + entry.getKey() + "'"));
                    if(!registered.equals(entry.getValue().flag())){
                        throw error(pointer, "stored flag definition does not match registry");
                    }
                    try {
                        result.add(entry.getKey(), plainToJson(encodeFlag(entry.getValue())));
                    } catch(IllegalArgumentException exception){
                        throw new DocumentFormatException(pointer, exception.getMessage(), exception);
                    }
                });
        return result;
    }

    private static JsonObject encodeModules(RegionRecord record){
        JsonObject modules = new JsonObject();
        modules.add("music", encodeMusic(record.music()));
        modules.add("commands", encodeCommands(record.commands()));
        return modules;
    }

    private static JsonObject encodeMusic(RegionMusicProfile profile){
        JsonObject music = new JsonObject();
        music.add("binding", encodeBinding(profile.getBinding()));
        JsonObject channels = new JsonObject();
        profile.getChannels().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> channels.add(
                        entry.getKey(),
                        encodeMusicChannel(entry.getValue())
                ));
        music.add("channels", channels);
        return music;
    }

    private static JsonObject encodeMusicChannel(RegionMusicChannel channel){
        JsonObject result = new JsonObject();
        result.addProperty("policy", channel.getPolicy().name().toLowerCase(java.util.Locale.ROOT));
        result.addProperty("order", channel.getOrder());
        result.addProperty("random", channel.isRandom());
        result.addProperty("loop", channel.isLoop());
        result.addProperty("volume", channel.getVolume());
        result.addProperty("pitch", channel.getPitch());
        result.addProperty("overwrite", channel.isOverwrite());
        JsonArray tracks = new JsonArray();
        for(MusicTrack track : channel.getTracks()){
            JsonObject encoded = new JsonObject();
            encoded.addProperty("id", track.getId());
            encoded.addProperty("sound", track.getSound());
            encoded.addProperty("duration", track.getDurationSeconds());
            tracks.add(encoded);
        }
        result.add("tracks", tracks);
        return result;
    }

    private static JsonObject encodeCommands(RegionCommandProfile profile){
        JsonObject result = new JsonObject();
        result.add("binding", encodeBinding(profile.getBinding()));
        result.add("enter", strings(profile.getEnterCommands()));
        result.add("leave", strings(profile.getLeaveCommands()));
        return result;
    }

    private ModuleRegionBinding decodeBinding(JsonElement element,
                                              String pointer) {
        if(element == null || element.isJsonNull()) {
            return ModuleRegionBinding.nativeSelf();
        }
        JsonObject object = requireObject(element, pointer);
        rejectUnknownFields(object, BINDING_FIELDS, pointer);
        JsonElement providerElement = requireField(object, "provider", pointer);
        String provider = requireNonBlankString(
                providerElement,
                pointer + "/provider"
        );
        JsonElement regionElement = requireField(object, "region", pointer);
        String region = requireNonBlankString(
                regionElement,
                pointer + "/region"
        );
        try {
            ModuleRegionBinding binding = ModuleRegionBinding.toProvider(
                    provider,
                    region
            );
            ProviderRegionReference normalized = binding.explicitTarget()
                    .orElseThrow();
            if(!providerElement.getAsString().equals(provider)
                    || !provider.equals(normalized.providerId())) {
                throw error(pointer + "/provider", "provider ID is not canonical");
            }
            if(!regionElement.getAsString().equals(region)
                    || !region.equals(normalized.regionId())) {
                throw error(pointer + "/region", "provider region ID is not canonical");
            }
            return binding;
        } catch(DocumentFormatException exception) {
            throw exception;
        } catch(IllegalArgumentException exception) {
            throw new DocumentFormatException(pointer, exception.getMessage(), exception);
        }
    }

    private static JsonElement encodeBinding(ModuleRegionBinding binding) {
        if(binding.isNativeSelf()) {
            return JsonNull.INSTANCE;
        }
        ProviderRegionReference target = binding.explicitTarget().orElseThrow();
        JsonObject result = new JsonObject();
        result.addProperty("provider", target.providerId());
        result.addProperty("region", target.regionId());
        return result;
    }

    private static JsonObject encodeWorld(WorldId world){
        JsonObject result = new JsonObject();
        result.addProperty("uuid", world.uuid().toString());
        result.addProperty("key", world.namespacedKey());
        return result;
    }

    private static JsonObject encodeDomain(RegionDomain domain){
        JsonObject result = new JsonObject();
        JsonArray players = new JsonArray();
        domain.players().stream()
                .map(UUID::toString)
                .sorted()
                .forEach(players::add);
        JsonArray groups = new JsonArray();
        domain.groups().stream().sorted().forEach(groups::add);
        result.add("players", players);
        result.add("groups", groups);
        return result;
    }

    private void validateFlagScopes(Region region){
        for(Map.Entry<String, FlagValue<?>> entry : region.flags().entrySet()){
            Flag<?> registered = flags.find(entry.getKey()).orElseThrow(() ->
                    error("/flags/" + escape(entry.getKey()), "unknown flag '" + entry.getKey() + "'"));
            if(!registered.scope().accepts(region)){
                throw error(
                        "/flags/" + escape(entry.getKey()),
                        "flag is not valid for this region scope"
                );
            }
        }
    }

    private static void validateRegionInvariants(Region region){
        boolean globalId = region.key().isGlobal();
        boolean globalShape = region.shape() == GlobalShape.INSTANCE;
        if(globalId){
            if(!globalShape){
                throw error("/shape", "global region must use the global shape");
            }
            if(region.parent().isPresent()){
                throw error("/parent", "global region cannot have a parent");
            }
        } else {
            if(globalShape){
                throw error("/shape", "only __global__ may use the global shape");
            }
            RegionKey parent = region.parent().orElseThrow(() ->
                    error("/parent", "non-global region must have a parent"));
            if(!parent.world().equals(region.key().world())){
                throw error("/parent", "parent must belong to the same world");
            }
            if(parent.equals(region.key())){
                throw error("/parent", "region cannot parent itself");
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static FlagValue<?> decodeFlag(Flag flag, Object encoded){
        Object decoded = flag.codec().decode(encoded);
        return flag.value(decoded);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object encodeFlag(FlagValue<?> value){
        Flag flag = value.flag();
        return flag.codec().encode(value.value());
    }

    private static Object jsonToPlain(JsonElement element){
        if(element == null || element.isJsonNull()){
            return null;
        }
        if(element.isJsonObject()){
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            for(Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()){
                result.put(entry.getKey(), jsonToPlain(entry.getValue()));
            }
            return result;
        }
        if(element.isJsonArray()){
            ArrayList<Object> result = new ArrayList<>();
            for(JsonElement item : element.getAsJsonArray()){
                result.add(jsonToPlain(item));
            }
            return result;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if(primitive.isBoolean()){
            return primitive.getAsBoolean();
        }
        if(primitive.isString()){
            return primitive.getAsString();
        }
        if(primitive.isNumber()){
            return primitive.getAsBigDecimal();
        }
        throw new IllegalArgumentException("unsupported JSON primitive");
    }

    private static JsonElement plainToJson(Object value){
        if(value == null){
            return JsonNull.INSTANCE;
        }
        if(value instanceof String string){
            return new JsonPrimitive(string);
        }
        if(value instanceof Boolean bool){
            return new JsonPrimitive(bool);
        }
        if(value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger
                || value instanceof BigDecimal){
            return new JsonPrimitive((Number) value);
        }
        if(value instanceof Float number){
            if(!Float.isFinite(number)){
                throw new IllegalArgumentException("flag codec produced a non-finite number");
            }
            return new JsonPrimitive(number);
        }
        if(value instanceof Double number){
            if(!Double.isFinite(number)){
                throw new IllegalArgumentException("flag codec produced a non-finite number");
            }
            return new JsonPrimitive(number);
        }
        if(value instanceof Map<?, ?> map){
            JsonObject result = new JsonObject();
            map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .forEach(entry -> {
                        if(!(entry.getKey() instanceof String key)){
                            throw new IllegalArgumentException("flag object keys must be strings");
                        }
                        result.add(key, plainToJson(entry.getValue()));
                    });
            return result;
        }
        if(value instanceof Iterable<?> iterable){
            JsonArray result = new JsonArray();
            for(Object item : iterable){
                result.add(plainToJson(item));
            }
            return result;
        }
        throw new IllegalArgumentException(
                "flag codec produced unsupported value " + value.getClass().getName()
        );
    }

    private static JsonArray strings(Collection<String> values){
        JsonArray result = new JsonArray();
        for(String value : values){
            result.add(value);
        }
        return result;
    }

    private static JsonElement requireField(JsonObject object,
                                            String field,
                                            String pointer){
        if(!object.has(field)){
            throw error(append(pointer, field), "required field is missing");
        }
        return object.get(field);
    }

    private static JsonObject requireObject(JsonElement element, String pointer){
        if(element == null || !element.isJsonObject()){
            throw error(pointer, "expected an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonElement element, String pointer){
        if(element == null || !element.isJsonArray()){
            throw error(pointer, "expected an array");
        }
        return element.getAsJsonArray();
    }

    private static String requireNonBlankString(JsonElement element, String pointer){
        if(element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()){
            throw error(pointer, "expected a non-blank string");
        }
        String result = element.getAsString();
        if(result.trim().isEmpty()){
            throw error(pointer, "expected a non-blank string");
        }
        return result.trim();
    }

    private static String requireCanonicalRegionId(JsonElement element,
                                                   String pointer){
        if(element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()){
            throw error(pointer, "expected a canonical region ID string");
        }
        String raw = element.getAsString();
        String normalized;
        try {
            normalized = RegionKey.normalizeId(raw);
        } catch(IllegalArgumentException exception){
            throw new DocumentFormatException(pointer, exception.getMessage(), exception);
        }
        if(!raw.equals(normalized)){
            throw error(pointer, "region ID is not canonical: " + raw);
        }
        return normalized;
    }

    private static boolean requireBoolean(JsonElement element, String pointer){
        if(element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isBoolean()){
            throw error(pointer, "expected a boolean");
        }
        return element.getAsBoolean();
    }

    private static int requireInt(JsonElement element, String pointer){
        long value = requireLong(element, pointer);
        if(value < Integer.MIN_VALUE || value > Integer.MAX_VALUE){
            throw error(pointer, "integer is outside the 32-bit range");
        }
        return (int) value;
    }

    private static long requireLong(JsonElement element, String pointer){
        BigDecimal value = requireDecimal(element, pointer);
        try {
            return value.longValueExact();
        } catch(ArithmeticException exception){
            throw new DocumentFormatException(pointer, "expected a 64-bit integer", exception);
        }
    }

    private static float requireFloat(JsonElement element, String pointer){
        BigDecimal value = requireDecimal(element, pointer);
        float result = value.floatValue();
        if(!Float.isFinite(result)){
            throw error(pointer, "number is outside the finite float range");
        }
        return result == 0.0f ? 0.0f : result;
    }

    private static BigDecimal requireDecimal(JsonElement element, String pointer){
        if(element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()){
            throw error(pointer, "expected a number");
        }
        try {
            return element.getAsBigDecimal();
        } catch(NumberFormatException exception){
            throw new DocumentFormatException(pointer, "invalid number", exception);
        }
    }

    private static void rejectUnknownFields(JsonObject object,
                                            Set<String> allowed,
                                            String pointer){
        for(String field : object.keySet()){
            if(!allowed.contains(field)){
                throw error(append(pointer, field), "unknown field '" + field + "'");
            }
        }
    }

    private static String append(String pointer, String segment){
        return pointer + "/" + escape(segment);
    }

    private static String escape(String segment){
        return segment.replace("~", "~0").replace("/", "~1");
    }

    private static DocumentFormatException rebase(String prefix,
                                                  DocumentFormatException exception){
        String child = exception.pointer();
        String pointer = child == null || child.isEmpty() ? prefix : prefix + child;
        return new DocumentFormatException(pointer, stripLocation(exception.getMessage()), exception);
    }

    private static DocumentFormatException strictError(
            StrictJson.StrictJsonException exception){
        return new DocumentFormatException(
                exception.pointer(),
                stripLocation(exception.getMessage()),
                exception
        );
    }

    private static String stripLocation(String message){
        if(message == null){
            return "invalid document";
        }
        int separator = message.indexOf(": ");
        return separator < 0 ? message : message.substring(separator + 2);
    }

    private static DocumentFormatException error(String pointer, String message){
        return new DocumentFormatException(pointer, message);
    }

    private record Modules(RegionMusicProfile music, RegionCommandProfile commands) {
    }
}
