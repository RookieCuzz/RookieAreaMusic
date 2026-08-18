package io.github.rookiecuzz.rookieregions.module.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Final deterministic layers selected for one configured channel. */
public final class ResolvedMusicChannel {
    private final MusicChannelDefinition definition;
    private final List<ResolvedMusicLayer> layers;
    private final boolean blocked;
    private final Integer blockingOrder;

    ResolvedMusicChannel(MusicChannelDefinition definition,
                         List<ResolvedMusicLayer> layers,
                         boolean blocked,
                         Integer blockingOrder) {
        this.definition = definition;
        this.layers = Collections.unmodifiableList(new ArrayList<>(layers));
        this.blocked = blocked;
        this.blockingOrder = blockingOrder;
    }

    public MusicChannelDefinition getDefinition() {
        return definition;
    }

    public List<ResolvedMusicLayer> getLayers() {
        return layers;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public Integer getBlockingOrder() {
        return blockingOrder;
    }
}
