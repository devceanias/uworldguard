package com.tricrotism.uworldguard.integration;

import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.tricrotism.uworldguard.region.ApplicableRegionSet;
import com.tricrotism.uworldguard.region.ProtectedCuboidRegion;
import com.tricrotism.uworldguard.region.RegionManager;
import com.tricrotism.uworldguard.util.BlockVector3;
import com.tricrotism.uworldguard.wgcompat.RegionAdapters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorldGuard API edits to a region's owners and members, and saves through its manager, keep what
 * they should and mark the world only when something changed.
 */
class CompatDomainEditsTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final RegionManager manager = new RegionManager();
    private final ProtectedCuboidRegion plot =
        new ProtectedCuboidRegion("plot", BlockVector3.at(0, 0, 0), BlockVector3.at(15, 255, 15));

    private ProtectedRegion added() {
        manager.addRegion(plot);
        return RegionAdapters.region(plot, manager);
    }

    @Test
    void handingBackTheRegionsOwnDomainsKeepsThem() {
        final ProtectedRegion region = added();
        region.getOwners().addPlayer(OWNER);
        region.getOwners().addGroup("staff");
        region.getMembers().addPlayer(MEMBER);
        region.getMembers().addGroup("guests");

        region.setOwners(region.getOwners());
        region.setMembers(region.getMembers());

        assertTrue(plot.getOwners().containsPlayer(OWNER));
        assertTrue(plot.getOwners().containsGroup("staff"));
        assertTrue(plot.getMembers().containsPlayer(MEMBER));
        assertTrue(plot.getMembers().containsGroup("guests"));

        final DefaultDomain other = new DefaultDomain();
        other.addPlayer(MEMBER);
        region.setOwners(other);
        other.addPlayer(OWNER);

        assertFalse(plot.getOwners().containsPlayer(OWNER));
        assertTrue(plot.getOwners().containsPlayer(MEMBER));
        assertFalse(plot.getOwners().containsGroup("staff"));
    }

    @Test
    void aDomainEditThatChangesNothingDoesNotMarkTheWorld() {
        final ProtectedRegion region = added();
        region.getOwners().addPlayer(OWNER);
        assertTrue(manager.clearDirty());
        final ApplicableRegionSet before = manager.getApplicableRegions(3, 64, 3);
        assertSame(before, manager.getApplicableRegions(3, 64, 3));

        region.getOwners().addPlayer(OWNER);
        region.getOwners().removePlayer(MEMBER);
        region.getMembers().getPlayerDomain().removePlayer(OWNER);

        assertFalse(manager.clearDirty());
        assertSame(before, manager.getApplicableRegions(3, 64, 3));
    }

    @Test
    void aMemberAddMarksTheWorldAndKeepsTheSharedSet() {
        final ProtectedRegion region = added();
        manager.clearDirty();
        final ApplicableRegionSet before = manager.getApplicableRegions(3, 64, 3);
        assertSame(before, manager.getApplicableRegions(3, 64, 3));

        region.getMembers().addPlayer(MEMBER);

        assertTrue(plot.getMembers().containsPlayer(MEMBER));
        assertTrue(manager.clearDirty(), "the world is marked for saving");
        assertSame(before, manager.getApplicableRegions(3, 64, 3), "a membership edit retires no set");
    }

    @Test
    void saveMarksTheWorldWithoutRetiringItsSharedSets() throws StorageException {
        manager.addRegion(plot);
        final com.sk89q.worldguard.protection.managers.RegionManager wrapped =
            RegionAdapters.manager(manager, "world");
        manager.clearDirty();
        final ApplicableRegionSet before = manager.getApplicableRegions(3, 64, 3);
        assertSame(before, manager.getApplicableRegions(3, 64, 3));

        wrapped.save();
        assertTrue(manager.clearDirty());
        assertTrue(wrapped.saveChanges());
        assertTrue(manager.clearDirty());

        assertSame(before, manager.getApplicableRegions(3, 64, 3));
    }

    @Test
    void clearingAnEmptyWorldDoesNotMarkIt() {
        final com.sk89q.worldguard.protection.managers.RegionManager wrapped =
            RegionAdapters.manager(manager, "world");
        manager.clearDirty();

        wrapped.setRegions(List.of());

        assertFalse(manager.clearDirty());
    }
}
