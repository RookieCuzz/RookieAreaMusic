package io.github.rookiecuzz.rookieregions.persistence;

import com.google.gson.JsonObject;
import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionDomain;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.core.shape.ShapeRelation;
import io.github.rookiecuzz.rookieregions.module.commands.RegionCommandProfile;
import io.github.rookiecuzz.rookieregions.module.music.MusicPolicyMode;
import io.github.rookiecuzz.rookieregions.module.music.MusicTrack;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicChannel;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicProfile;
import io.github.rookiecuzz.rookieregions.persistence.codec.DocumentFormatException;
import io.github.rookiecuzz.rookieregions.persistence.codec.ShapeJsonCodec;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.rule.FlagRegistry;
import io.github.rookiecuzz.rookieregions.rule.State;
import io.github.rookiecuzz.rookieregions.rule.StateFlag;
import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;
import io.github.rookiecuzz.rookieregions.runtime.ModuleRegionBinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionDocumentCodecTest {
    private static final WorldId WORLD = new WorldId(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            "minecraft:overworld"
    );
    private static final UUID OWNER = UUID.fromString(
            "20000000-0000-0000-0000-000000000002"
    );

    private final RegionDocumentCodec codec = new RegionDocumentCodec();

    @Test
    void registeredExtensionFlagParticipatesInStrictRoundTrip() {
        StateFlag custom = new StateFlag("example.flight");
        java.util.ArrayList<io.github.rookiecuzz.rookieregions.rule.Flag<?>> flags =
                new java.util.ArrayList<>(ProtectionFlags.REGISTRY.values());
        flags.add(custom);
        RegionDocumentCodec extensionCodec = new RegionDocumentCodec(
                new FlagRegistry(flags), ShapeJsonCodec.INSTANCE
        );
        Region region = Region.builder(
                        new RegionKey(WORLD, "extension-zone"),
                        new CuboidShape(0, 0, 0, 10, 10, 10)
                )
                .parent(RegionKey.global(WORLD))
                .flag(custom, State.DENY)
                .build();

        RegionRecord decoded = extensionCodec.decode(
                extensionCodec.encodeToString(RegionRecord.coreOnly(region))
        );

        assertEquals(State.DENY, decoded.region().flag(custom).orElseThrow().value());
    }

    @Test
    void roundTripsCompleteRecordAndWritesCanonicalOrdering() {
        RegionRecord source = fullRecord(7);

        String encoded = codec.encodeToString(source);
        RegionRecord decoded = codec.decode(encoded);

        assertRegionEquals(source.region(), decoded.region());
        assertEquals(source.music(), decoded.music());
        assertEquals(source.commands(), decoded.commands());
        assertEquals(encoded, codec.encodeToString(decoded));

        assertOrdered(encoded,
                "\"schemaVersion\"", "\"id\"", "\"world\"", "\"parent\"",
                "\"priority\"", "\"shape\"", "\"owners\"", "\"members\"",
                "\"flags\"", "\"modules\""
        );
        assertTrue(encoded.contains("\"groups\":[\"admins\",\"builders\"]"));
        assertTrue(encoded.contains("\"enter\":[\"say welcome\",\"title @s clear\"]"));
        assertEquals(
                "forest",
                decoded.music().getBinding().explicitTarget()
                        .orElseThrow().regionId()
        );
    }

    @Test
    void rejectsUnknownFieldsAtEverySchemaLayer() {
        assertUnknown("/extra", root -> root.addProperty("extra", true));
        assertUnknown("/world/extra", root ->
                root.getAsJsonObject("world").addProperty("extra", true));
        assertUnknown("/owners/extra", root ->
                root.getAsJsonObject("owners").addProperty("extra", true));
        assertUnknown("/modules/weather", root ->
                root.getAsJsonObject("modules").add("weather", new JsonObject()));
        assertUnknown("/modules/music/extra", root ->
                root.getAsJsonObject("modules").getAsJsonObject("music")
                        .addProperty("extra", true));
        assertUnknown("/modules/music/binding/extra", root ->
                root.getAsJsonObject("modules").getAsJsonObject("music")
                        .getAsJsonObject("binding").addProperty("extra", true));
        assertUnknown("/modules/music/channels/ambient/extra", root ->
                ambient(root).addProperty("extra", true));
        assertUnknown("/modules/music/channels/ambient/tracks/0/extra", root ->
                ambient(root).getAsJsonArray("tracks").get(0).getAsJsonObject()
                        .addProperty("extra", true));
        assertUnknown("/modules/commands/extra", root ->
                root.getAsJsonObject("modules").getAsJsonObject("commands")
                        .addProperty("extra", true));
    }

    @Test
    void rejectsUnknownFlagsAndBadKnownFlagValuesAtTheirPointers() {
        JsonObject unknown = codec.encode(fullRecord(0));
        unknown.getAsJsonObject("flags").addProperty("plugin.unknown", "allow");
        assertPointer("/flags/plugin.unknown", unknown);

        JsonObject invalid = codec.encode(fullRecord(0));
        invalid.getAsJsonObject("flags")
                .addProperty(ProtectionFlags.BUILD.name(), "sometimes");
        assertPointer("/flags/build", invalid);
    }

    @Test
    void duplicateKeysAreRejectedWithExactNestedPointers() {
        String document = codec.encodeToString(fullRecord(0));
        String duplicateRoot = document.replaceFirst(
                "\\\"schemaVersion\\\":1,",
                "\"schemaVersion\":1,\"schemaVersion\":1,"
        );
        assertPointer("/schemaVersion", duplicateRoot);

        String duplicateWorld = document.replaceFirst(
                "\\\"uuid\\\":\\\"([^\\\"]+)\\\",",
                "\"uuid\":\"$1\",\"uuid\":\"$1\","
        );
        assertPointer("/world/uuid", duplicateWorld);
    }

    @Test
    void enforcesGlobalAndLocalRegionInvariants() {
        RegionRecord valid = globalRecord();
        RegionRecord roundTrip = codec.decode(codec.encodeToString(valid));
        assertEquals(Integer.MIN_VALUE, roundTrip.region().priority());
        assertEquals(GlobalShape.INSTANCE, roundTrip.region().shape());

        JsonObject wrongShape = codec.encode(valid);
        wrongShape.add("shape", ShapeJsonCodec.INSTANCE.encode(
                new CuboidShape(0, 0, 0, 1, 1, 1)
        ));
        assertPointer("/shape", wrongShape);

        JsonObject parented = codec.encode(valid);
        parented.addProperty("parent", "somewhere");
        assertPointer("/parent", parented);

        JsonObject owned = codec.encode(valid);
        owned.getAsJsonObject("owners").getAsJsonArray("players")
                .add(OWNER.toString());
        RegionRecord ownedGlobal = codec.decode(owned);
        assertTrue(ownedGlobal.region().owners().players().contains(OWNER));

        JsonObject localWithoutParent = codec.encode(fullRecord(0));
        localWithoutParent.add("parent", null);
        assertPointer("/parent", localWithoutParent);

        JsonObject localGlobalShape = codec.encode(fullRecord(0));
        localGlobalShape.add("shape", ShapeJsonCodec.INSTANCE.encode(GlobalShape.INSTANCE));
        assertPointer("/shape", localGlobalShape);
    }

    @Test
    void requiresFixedFieldsAndSchemaVersion() {
        JsonObject missing = codec.encode(fullRecord(0));
        missing.remove("modules");
        assertPointer("/modules", missing);

        JsonObject missingBinding = codec.encode(fullRecord(0));
        missingBinding.getAsJsonObject("modules").getAsJsonObject("music")
                .remove("binding");
        assertPointer("/modules/music/binding", missingBinding);

        JsonObject future = codec.encode(fullRecord(0));
        future.addProperty("schemaVersion", RegionDocumentCodec.SCHEMA_VERSION + 1);
        assertPointer("/schemaVersion", future);
    }

    @Test
    void rejectsIdsThatWouldRequireCaseOrWhitespaceNormalization() {
        JsonObject id = codec.encode(fullRecord(0));
        id.addProperty("id", "Spawn");
        assertPointer("/id", id);

        JsonObject parent = codec.encode(fullRecord(0));
        parent.addProperty("parent", " __global__ ");
        assertPointer("/parent", parent);

        JsonObject provider = codec.encode(fullRecord(0));
        provider.getAsJsonObject("modules").getAsJsonObject("music")
                .getAsJsonObject("binding").addProperty("provider", "WorldGuard");
        assertPointer("/modules/music/binding/provider", provider);

        JsonObject externalRegion = codec.encode(fullRecord(0));
        externalRegion.getAsJsonObject("modules").getAsJsonObject("music")
                .getAsJsonObject("binding").addProperty("region", " Forest ");
        assertPointer("/modules/music/binding/region", externalRegion);
    }

    private void assertUnknown(String pointer, Mutation mutation) {
        JsonObject document = codec.encode(fullRecord(0));
        mutation.apply(document);
        assertPointer(pointer, document);
    }

    private void assertPointer(String expected, JsonObject document) {
        assertPointer(expected, document.toString());
    }

    private void assertPointer(String expected, String document) {
        DocumentFormatException exception = assertThrows(
                DocumentFormatException.class,
                () -> codec.decode(document)
        );
        assertEquals(expected, exception.pointer());
    }

    private static JsonObject ambient(JsonObject root) {
        return root.getAsJsonObject("modules")
                .getAsJsonObject("music")
                .getAsJsonObject("channels")
                .getAsJsonObject("ambient");
    }

    private static RegionRecord fullRecord(int priority) {
        RegionDomain owners = new RegionDomain(
                List.of(OWNER),
                List.of("builders", "Admins")
        );
        RegionDomain members = new RegionDomain(
                List.of(UUID.fromString("30000000-0000-0000-0000-000000000003")),
                List.of("visitors")
        );
        Region region = Region.builder(
                        new RegionKey(WORLD, "spawn"),
                        new CuboidShape(0, -64, 0, 16, 320, 16)
                )
                .parent(RegionKey.global(WORLD))
                .priority(priority)
                .owners(owners)
                .members(members)
                .flag(ProtectionFlags.BUILD, State.DENY)
                .flag(ProtectionFlags.PVP, State.ALLOW)
                .build();
        RegionMusicChannel channel = RegionMusicChannel.builder()
                .policy(MusicPolicyMode.REPLACE)
                .order(4)
                .random(true)
                .loop(false)
                .volume(0.75f)
                .pitch(1.25f)
                .overwrite(false)
                .tracks(List.of(new MusicTrack(
                        "theme", "minecraft:music.overworld", 90
                )))
                .build();
        return new RegionRecord(
                region,
                new RegionMusicProfile(
                        Map.of("ambient", channel),
                        ModuleRegionBinding.toProvider("worldguard", "forest")
                ),
                new RegionCommandProfile(
                        List.of("/say welcome", "title @s clear"),
                        List.of("say goodbye"),
                        ModuleRegionBinding.toProvider("worldguard", "town")
                )
        );
    }

    private static RegionRecord globalRecord() {
        return RegionRecord.coreOnly(
                Region.builder(RegionKey.global(WORLD), GlobalShape.INSTANCE)
                        .priority(Integer.MIN_VALUE)
                        .build()
        );
    }

    private static void assertRegionEquals(Region expected, Region actual) {
        assertEquals(expected.key(), actual.key());
        assertEquals(expected.priority(), actual.priority());
        assertEquals(expected.parent(), actual.parent());
        assertEquals(expected.owners().players(), actual.owners().players());
        assertEquals(expected.owners().groups(), actual.owners().groups());
        assertEquals(expected.members().players(), actual.members().players());
        assertEquals(expected.members().groups(), actual.members().groups());
        assertEquals(ShapeRelation.EQUAL, expected.shape().relationTo(actual.shape()));
        assertEquals(expected.flags().keySet(), actual.flags().keySet());
        for(String name : expected.flags().keySet()) {
            assertEquals(
                    expected.flags().get(name).value(),
                    actual.flags().get(name).value()
            );
        }
    }

    private static void assertOrdered(String value, String... fragments) {
        int previous = -1;
        for(String fragment : fragments) {
            int current = value.indexOf(fragment, previous + 1);
            assertTrue(current > previous, () -> fragment + " was out of order");
            previous = current;
        }
    }

    @FunctionalInterface
    private interface Mutation {
        void apply(JsonObject object);
    }
}
