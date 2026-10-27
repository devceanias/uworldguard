package com.tricrotism.uworldguard.integration;

import com.tricrotism.uworldguard.region.ProtectedCuboidRegion;
import com.tricrotism.uworldguard.region.RegionManager;
import com.tricrotism.uworldguard.util.BlockVector3;
import com.tricrotism.uworldguard.wgcompat.WrappedRegionSet;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A WorldGuard API view is made once per engine set and shared by every query in its claim, so a
 * consumer cannot change what the next one sees.
 */
class CompatViewTest {

    @Test
    void queriesInOneClaimShareOneViewThatCannotBeChanged() {
        final RegionManager manager = new RegionManager();
        manager.addRegion(new ProtectedCuboidRegion("a", BlockVector3.at(0, 0, 0), BlockVector3.at(15, 255, 15)));
        manager.addRegion(new ProtectedCuboidRegion("b", BlockVector3.at(16, 0, 0), BlockVector3.at(31, 255, 15)));

        final WrappedRegionSet first = WrappedRegionSet.of(manager.getApplicableRegions(3, 64, 3), manager);
        final WrappedRegionSet second = WrappedRegionSet.of(manager.getApplicableRegions(9, 70, 9), manager);
        final WrappedRegionSet other = WrappedRegionSet.of(manager.getApplicableRegions(20, 64, 3), manager);

        assertSame(first, second);
        assertNotSame(first, other);
        assertEquals("a", first.getRegions().iterator().next().getId());
        assertThrows(UnsupportedOperationException.class, () -> first.getRegions().clear());
        final Iterator<?> regions = first.iterator();
        regions.next();
        assertThrows(UnsupportedOperationException.class, regions::remove);
    }
}
