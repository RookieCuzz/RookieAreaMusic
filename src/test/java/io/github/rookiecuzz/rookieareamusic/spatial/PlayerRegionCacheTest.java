package io.github.rookiecuzz.rookieareamusic.spatial;

import io.github.rookiecuzz.rookieareamusic.config.AreaDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerRegionCacheTest {
    @Test
    void returnsOnlyAnExactWorldAndPositionMatch(){
        PlayerRegionCache cache = new PlayerRegionCache();
        UUID player = UUID.randomUUID();
        AreaDto area = AreaDto.builder().uuid("spawn").build();

        cache.put(player, "world", 1.25, 64.0, -2.75, Arrays.asList(area));

        assertEquals(Arrays.asList(area), cache.get(player, "world", 1.25, 64.0, -2.75));
        assertNull(cache.get(player, "world", 1.26, 64.0, -2.75));
        assertNull(cache.get(player, "world", 1.25, 64.01, -2.75));
        assertNull(cache.get(player, "world_nether", 1.25, 64.0, -2.75));
    }

    @Test
    void storesAnImmutableSnapshot(){
        PlayerRegionCache cache = new PlayerRegionCache();
        UUID player = UUID.randomUUID();
        AreaDto area = AreaDto.builder().uuid("spawn").build();
        List<AreaDto> mutable = new ArrayList<>();
        mutable.add(area);

        cache.put(player, "world", 0.5, 64.0, 0.5, mutable);
        mutable.clear();

        List<AreaDto> cached = cache.get(player, "world", 0.5, 64.0, 0.5);
        assertEquals(Arrays.asList(area), cached);
        assertThrows(UnsupportedOperationException.class, cached::clear);
    }

    @Test
    void clearsOnePlayerOrAllPlayers(){
        PlayerRegionCache cache = new PlayerRegionCache();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        cache.put(first, "world", 0.5, 64.0, 0.5, null);
        cache.put(second, "world", 0.5, 64.0, 0.5, null);

        cache.clear(first);
        assertNull(cache.get(first, "world", 0.5, 64.0, 0.5));
        assertEquals(1, cache.size());

        cache.clearAll();
        assertNull(cache.get(second, "world", 0.5, 64.0, 0.5));
        assertEquals(0, cache.size());
    }
}
