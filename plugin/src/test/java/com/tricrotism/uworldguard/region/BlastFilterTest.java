package com.tricrotism.uworldguard.region;

import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.storage.RegionStore;
import com.tricrotism.uworldguard.util.BlockVector3;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A blast keeps exactly the blocks a lookup per block would, whichever way it finds a block's regions:
 * the axis tables, the per-block test for shapes that are not boxes, a blast too spread out for the
 * tables, or more crossing regions than a mask holds.
 */
class BlastFilterTest {

    private static final RegionStore NO_STORE = new RegionStore() {
        @Override
        public void load(final String worldName, final RegionManager manager) {}

        @Override
        public void save(final String worldName, final RegionManager manager) {}
    };

    private WorldMock world;
    private RegionManager manager;
    private RegionQuery query;

    @BeforeEach
    void setUp() {
        final ServerMock server = MockBukkit.mock();
        final PluginMock plugin = MockBukkit.createMockPlugin("uWorldGuard");
        world = server.addSimpleWorld("world");
        final File dataFolder = plugin.getDataFolder();
        assertTrue(dataFolder.exists() || dataFolder.mkdirs());
        final RegionContainerImpl container = new RegionContainerImpl(plugin, NO_STORE);
        container.loadAll();
        query = container.createQuery();
        manager = container.get(world);
        assertNotNull(manager);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ProtectedCuboidRegion cuboid(final String id, final int x1, final int y1, final int z1,
                                                final int x2, final int y2, final int z2) {
        return new ProtectedCuboidRegion(id, BlockVector3.at(x1, y1, z1), BlockVector3.at(x2, y2, z2));
    }

    private static void deny(final ProtectedRegion region, final int priority) {
        region.setFlag(Flags.TNT, State.DENY);
        region.setPriority(priority);
    }

    private List<Block> cube(final int cx, final int cy, final int cz, final int r) {
        final List<Block> blocks = new ArrayList<>();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - r; y <= cy + r; y++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    blocks.add(world.getBlockAt(x, y, z));
                }
            }
        }
        return blocks;
    }

    private List<Block> keptMatchingPerBlock(final List<Block> blocks) {
        final List<Block> expected = blocks.stream().filter(block -> query.testState(block, Flags.TNT)).toList();
        final List<Block> actual = new ArrayList<>(blocks);
        query.removeDenied(world, actual, Flags.TNT);
        assertEquals(expected, actual);
        return actual;
    }

    private void assertMatchesPerBlock(final List<Block> blocks) {
        final List<Block> kept = keptMatchingPerBlock(blocks);
        assertTrue(kept.size() < blocks.size() && !kept.isEmpty(), "the blast should keep some blocks and lose some");
    }

    @Test
    void cuboidsOnlyMatchALookupPerBlock() {
        final ProtectedCuboidRegion big = cuboid("big", -100, 0, -100, 100, 255, 100);
        big.setFlag(Flags.TNT, State.ALLOW);
        manager.addRegion(big);
        final ProtectedCuboidRegion claim = cuboid("claim", 0, 60, 0, 15, 70, 15);
        deny(claim, 10);
        manager.addRegion(claim);
        final ProtectedCuboidRegion hole = cuboid("hole", 4, 62, 4, 6, 64, 6);
        hole.setFlag(Flags.TNT, State.ALLOW);
        hole.setPriority(20);
        manager.addRegion(hole);

        assertMatchesPerBlock(cube(3, 64, 3, 4));
    }

    @Test
    void aBlastWhoseCellsAgreeIsAnsweredWhole() {
        final ProtectedCuboidRegion west = cuboid("west", -16, 0, -16, -1, 255, 15);
        final ProtectedCuboidRegion east = cuboid("east", 0, 0, -16, 15, 255, 15);
        deny(west, 10);
        deny(east, 10);
        manager.addRegion(west);
        manager.addRegion(east);
        assertTrue(keptMatchingPerBlock(cube(0, 64, 0, 3)).isEmpty());

        west.setFlag(Flags.TNT, State.ALLOW);
        east.setFlag(Flags.TNT, State.ALLOW);
        assertEquals(343, keptMatchingPerBlock(cube(0, 64, 0, 3)).size());
    }

    @Test
    void boxesAndRoundShapesMatchALookupPerBlock() {
        final ProtectedCuboidRegion big = cuboid("big", -100, 0, -100, 100, 255, 100);
        big.setFlag(Flags.TNT, State.ALLOW);
        manager.addRegion(big);
        final ProtectedCuboidRegion claim = cuboid("claim", 0, 60, 0, 15, 70, 15);
        deny(claim, 10);
        manager.addRegion(claim);
        final ProtectedCuboidRegion hole = cuboid("hole", 4, 62, 4, 6, 64, 6);
        hole.setFlag(Flags.TNT, State.ALLOW);
        hole.setPriority(20);
        manager.addRegion(hole);
        final ProtectedCylinderRegion tower = new ProtectedCylinderRegion("tower", -3, 2, 3, 4, 58, 66);
        deny(tower, 5);
        manager.addRegion(tower);
        final ProtectedSphereRegion dome = new ProtectedSphereRegion("dome", 2, 70, -3, 4, 4, 4);
        deny(dome, 5);
        manager.addRegion(dome);

        assertMatchesPerBlock(cube(1, 64, 1, 7));
    }

    @Test
    void aBlastTooWideForTheTablesMatchesALookupPerBlock() {
        manager.addRegion(cuboid("wild", -5000, 0, -5000, 5000, 255, 5000));
        final ProtectedCuboidRegion east = cuboid("east", 1900, 0, -10, 2100, 255, 10);
        deny(east, 10);
        manager.addRegion(east);
        final List<Block> blocks = new ArrayList<>(cube(0, 64, 0, 2));
        blocks.addAll(cube(2000, 64, 0, 2));

        assertMatchesPerBlock(blocks);
    }

    @Test
    void moreCrossingRegionsThanAMaskHoldsMatchALookupPerBlock() {
        final Random random = new Random(7);
        for (int i = 0; i < 100; i++) {
            final int x = random.nextInt(16) - 8;
            final int y = 56 + random.nextInt(16);
            final int z = random.nextInt(16) - 8;
            final ProtectedCuboidRegion plot = cuboid("plot-" + i, x, y, z, x + random.nextInt(4), y + random.nextInt(4), z + random.nextInt(4));
            if (random.nextBoolean()) {
                deny(plot, random.nextInt(3));
            } else {
                plot.setFlag(Flags.TNT, State.ALLOW);
                plot.setPriority(random.nextInt(3));
            }
            manager.addRegion(plot);
        }

        assertMatchesPerBlock(cube(0, 64, 0, 8));
    }
}
