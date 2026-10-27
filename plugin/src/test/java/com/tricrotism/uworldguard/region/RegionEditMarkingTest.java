package com.tricrotism.uworldguard.region;

import com.sk89q.worldguard.protection.regions.ProtectedRegion.CircularInheritanceException;
import com.tricrotism.uworldguard.event.RegionMembershipChangeEvent;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.RegionGroup;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.flags.StateFlag;
import com.tricrotism.uworldguard.storage.RegionStore;
import com.tricrotism.uworldguard.util.BlockVector3;
import com.tricrotism.uworldguard.wgcompat.RegionAdapters;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edits that change nothing a shared region set holds leave the set in place, and each editor write
 * reaches the manager once, through the region it changed.
 */
class RegionEditMarkingTest {

    /**
     * Answers with every region it was told about and counts the puts.
     */
    private static final class CountingIndex implements SpatialIndex {
        final List<ProtectedRegion> known = new ArrayList<>();
        int puts;

        @Override
        public void put(final ProtectedRegion region) {
            puts++;
            if (!known.contains(region)) {
                known.add(region);
            }
        }

        @Override
        public void remove(final ProtectedRegion region) {
            known.remove(region);
        }

        @Override
        public List<ProtectedRegion> candidatesAt(final int x, final int y, final int z) {
            return new ArrayList<>(known);
        }

        @Override
        public List<ProtectedRegion> intersecting(
            final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ
        ) {
            return new ArrayList<>(known);
        }

        @Override
        public @Nullable State stateAcross(
            final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ,
            final StateFlag flag, final boolean groups
        ) {
            return null;
        }
    }

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ProtectedCuboidRegion claim() {
        return new ProtectedCuboidRegion("claim", BlockVector3.at(0, 0, 0), BlockVector3.at(32, 255, 32));
    }

    @Test
    void aMembershipEditKeepsTheSharedSet() {
        final RegionManager manager = new RegionManager();
        final ProtectedCuboidRegion region = claim();
        manager.addRegion(region);
        final RegionEditorImpl editor = new RegionEditorImpl(world, manager);
        final ApplicableRegionSet before = manager.getApplicableRegions(16, 64, 16);
        assertSame(before, manager.getApplicableRegions(17, 70, 17), "two points in the claim share a set");
        manager.clearDirty();

        final UUID friend = UUID.randomUUID();
        assertEquals(EditResult.APPLIED,
            editor.addPlayer(region, RegionMembershipChangeEvent.Role.MEMBER, friend, null));
        assertEquals(EditResult.APPLIED,
            editor.setGroupTrusted(region, RegionMembershipChangeEvent.Role.OWNER, "builders", true, null));

        assertSame(before, manager.getApplicableRegions(16, 64, 16), "a membership edit retires no set");
        assertTrue(before.canBuild(friend), "the kept set reads membership live");
        assertTrue(manager.clearDirty(), "the world is still marked for saving");
    }

    @Test
    void reAddingTheHeldInstanceKeepsTheSharedSetAndReachesTheIndex() {
        final RegionManager manager = new RegionManager();
        final CountingIndex index = new CountingIndex();
        manager.uwgUseIndex(index);
        final ProtectedCuboidRegion region = claim();
        manager.addRegion(region);
        final ApplicableRegionSet before = manager.getApplicableRegions(16, 64, 16);
        manager.clearDirty();
        index.puts = 0;

        manager.addRegion(region);

        assertSame(region, manager.getRegion("claim"));
        assertSame(before, manager.getApplicableRegions(16, 64, 16), "re-adding the same instance retires no set");
        assertTrue(manager.clearDirty(), "the world is still marked for saving");
        assertEquals(1, index.puts, "the index still hears of it, which is how a member edit reaches it");
    }

    @Test
    void setFlagReportsOnlyTheHalfThatChanged() {
        final RegionManager manager = new RegionManager();
        final ProtectedCuboidRegion region = claim();
        manager.addRegion(region);
        final RegionEditorImpl editor = new RegionEditorImpl(world, manager);

        final long valueAt = manager.flagEditCount();
        assertEquals(EditResult.APPLIED, editor.setFlag(region, Flags.PVP, State.DENY, null));
        assertEquals(valueAt + 1, manager.flagEditCount(), "a value edit leaves the unchanged group alone");

        final long groupAt = manager.flagEditCount();
        assertEquals(EditResult.APPLIED, editor.setFlag(region, Flags.PVP, State.DENY, RegionGroup.NON_MEMBERS, null));
        assertEquals(groupAt + 1, manager.flagEditCount(), "a group edit leaves the unchanged value alone");

        assertEquals(State.DENY, region.getFlags().get(Flags.PVP));
        assertEquals(RegionGroup.NON_MEMBERS, region.getFlagGroups().get(Flags.PVP));
    }

    @Test
    void groupPriorityAndParentEditsReportOnceAndStillMarkTheWorld() {
        final RegionManager manager = new RegionManager();
        final ProtectedCuboidRegion region = claim();
        final ProtectedCuboidRegion parent = new ProtectedCuboidRegion("parent",
            BlockVector3.at(-64, 0, -64), BlockVector3.at(64, 255, 64));
        manager.addRegion(region);
        manager.addRegion(parent);
        final RegionEditorImpl editor = new RegionEditorImpl(world, manager);
        editor.setFlag(region, Flags.PVP, State.DENY, null);
        manager.clearDirty();

        final long groupAt = manager.flagEditCount();
        assertEquals(EditResult.APPLIED, editor.setFlagGroup(region, Flags.PVP, RegionGroup.MEMBERS, null));
        assertEquals(groupAt + 1, manager.flagEditCount());
        assertTrue(manager.clearDirty());

        final long priorityAt = manager.flagEditCount();
        assertEquals(EditResult.APPLIED, editor.setPriority(region, 5, null));
        assertEquals(priorityAt + 1, manager.flagEditCount());
        assertTrue(manager.clearDirty());

        final long parentAt = manager.flagEditCount();
        assertEquals(EditResult.APPLIED, editor.setParent(region, parent, null));
        assertEquals(parentAt + 1, manager.flagEditCount());
        assertTrue(manager.clearDirty());
    }

    @Test
    void compatEditsMarkTheWorldThroughOneEngineReport() throws CircularInheritanceException {
        final RegionManager manager = new RegionManager();
        final ProtectedCuboidRegion region = claim();
        final ProtectedCuboidRegion parent = new ProtectedCuboidRegion("parent",
            BlockVector3.at(-64, 0, -64), BlockVector3.at(64, 255, 64));
        manager.addRegion(region);
        manager.addRegion(parent);
        final com.sk89q.worldguard.protection.regions.ProtectedRegion shim = RegionAdapters.region(region, manager);
        final com.sk89q.worldguard.protection.regions.ProtectedRegion shimParent =
            RegionAdapters.region(parent, manager);
        manager.clearDirty();

        final long flagAt = manager.flagEditCount();
        shim.setFlag(com.sk89q.worldguard.protection.flags.Flags.PVP,
            com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
        assertEquals(State.DENY, region.getFlags().get(Flags.PVP));
        assertEquals(flagAt + 1, manager.flagEditCount());
        assertTrue(manager.clearDirty());

        final long priorityAt = manager.flagEditCount();
        shim.setPriority(5);
        assertEquals(priorityAt + 1, manager.flagEditCount());
        assertTrue(manager.clearDirty());

        final long parentAt = manager.flagEditCount();
        shim.setParent(shimParent);
        assertSame(parent, region.getParent());
        assertEquals(parentAt + 1, manager.flagEditCount());
        assertTrue(manager.clearDirty());

        RegionAdapters.manager(manager, "world").removeRegion("claim");
        assertNull(manager.getRegion("claim"));
        assertTrue(manager.clearDirty());
    }

    @Test
    void aFailedSaveMarksTheWorldAgainAndKeepsTheSharedSet() {
        final RegionStore failing = new RegionStore() {
            @Override
            public void load(final String worldName, final RegionManager manager) {}

            @Override
            public void save(final String worldName, final RegionManager manager) throws IOException {
                throw new IOException("disk full");
            }
        };
        final RegionContainerImpl container =
            new RegionContainerImpl(MockBukkit.createMockPlugin("uWorldGuard"), failing);
        container.loadAll();
        final RegionManager manager = container.get(world);
        assertNotNull(manager);
        manager.addRegion(claim());
        final ApplicableRegionSet before = manager.getApplicableRegions(16, 64, 16);

        container.saveAll();
        server.getScheduler().waitAsyncTasksFinished();

        assertSame(before, manager.getApplicableRegions(16, 64, 16), "a failed write retires no set");
        assertTrue(manager.clearDirty(), "the failed write marks the world for the next save");
    }
}
