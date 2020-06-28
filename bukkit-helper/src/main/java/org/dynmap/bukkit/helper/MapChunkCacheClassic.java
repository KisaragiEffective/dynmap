package org.dynmap.bukkit.helper;

import org.bukkit.block.Biome;
import org.bukkit.ChunkSnapshot;
import org.dynmap.bukkit.helper.AbstractMapChunkCache.Snapshot;
import org.dynmap.renderer.DynmapBlockState;

import java.util.stream.IntStream;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class MapChunkCacheClassic extends AbstractMapChunkCache {

    public static class WrappedSnapshot implements Snapshot {
        private final ChunkSnapshot ss;
        private final int sectionmask;
        public WrappedSnapshot(ChunkSnapshot ss) {
            this.ss = ss;
            int mask = IntStream.range(0, 16).filter(ss::isSectionEmpty).map(i -> (1 << i)).reduce(0, (a, b) -> a | b);
            sectionmask = mask;
        }
        @Override
        public final DynmapBlockState getBlockType(int x, int y, int z) {
            if ((sectionmask & (1 << (y >> 4))) != 0)
                return DynmapBlockState.AIR;
            return BukkitVersionHelper.stateByID[(ss.getBlockTypeId(x, y, z) << 4) | ss.getBlockData(x, y, z)];
        }
        @Override
        public final int getBlockSkyLight(int x, int y, int z) {
            return ss.getBlockSkyLight(x, y, z);
        }
        @Override
        public final int getBlockEmittedLight(int x, int y, int z) {
            return ss.getBlockEmittedLight(x, y, z);
        }
        @Override
        public final int getHighestBlockYAt(int x, int z) {
            return ss.getHighestBlockYAt(x, z);
        }
        @Override
        public final Biome getBiome(int x, int z) {
            return ss.getBiome(x, z);
        }
        @Override
        public final boolean isSectionEmpty(int sy) {
            return (sectionmask & (1 << sy)) != 0;
        }
        @Override
        public final Object[] getBiomeBaseFromSnapshot() {
            return BukkitVersionHelper.helper.getBiomeBaseFromSnapshot(ss);
        }
    }

    /**
     * Construct empty cache
     */
    public MapChunkCacheClassic() {

    }

    @Override
    public Snapshot wrapChunkSnapshot(ChunkSnapshot css) {
        return new WrappedSnapshot(css);
    }
    
}
