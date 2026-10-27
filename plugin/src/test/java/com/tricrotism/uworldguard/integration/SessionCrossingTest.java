package com.tricrotism.uworldguard.integration;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.handler.Handler;
import com.tricrotism.uworldguard.region.ProtectedCuboidRegion;
import com.tricrotism.uworldguard.region.RegionManager;
import com.tricrotism.uworldguard.util.BlockVector3;
import com.tricrotism.uworldguard.wgcompat.WrappedRegionSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A WorldGuard session handler sees a crossing with exactly the regions entered and left, and nothing
 * for a move that stays in the same regions.
 */
class SessionCrossingTest {

    private static final class Recorder extends Handler {
        final List<Set<String>> entered = new ArrayList<>();
        final List<Set<String>> exited = new ArrayList<>();

        Recorder(final Session session) {
            super(session);
        }

        @Override
        public boolean onCrossBoundary(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet, final Set<ProtectedRegion> entered, final Set<ProtectedRegion> exited, final MoveType moveType) {
            this.entered.add(ids(entered));
            this.exited.add(ids(exited));
            return true;
        }
    }

    private static final class Mover extends Handler {
        Mover(final Session session) {
            super(session);
        }

        @Override
        public boolean testMoveTo(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet, final MoveType moveType) {
            return true;
        }
    }

    private static Set<String> ids(final Set<ProtectedRegion> regions) {
        final Set<String> out = new TreeSet<>();
        for (final ProtectedRegion region : regions) {
            out.add(region.getId());
        }
        return out;
    }

    private static WrappedRegionSet at(final RegionManager manager, final int x, final int z) {
        return new WrappedRegionSet(manager.getApplicableRegions(x, 64, z), manager);
    }

    @Test
    void aCrossingHandsOverOnlyTheRegionsEnteredAndLeft() {
        final RegionManager manager = new RegionManager();
        manager.addRegion(new ProtectedCuboidRegion("district", BlockVector3.at(0, 0, 0), BlockVector3.at(63, 255, 63)));
        manager.addRegion(new ProtectedCuboidRegion("a", BlockVector3.at(0, 0, 0), BlockVector3.at(15, 255, 15)));
        manager.addRegion(new ProtectedCuboidRegion("b", BlockVector3.at(16, 0, 0), BlockVector3.at(31, 255, 15)));
        final Session session = new Session(null);
        final Recorder recorder = new Recorder(session);
        session.register(recorder);

        assertNull(session.uwgTestMoveTo(null, null, null, MoveType.MOVE, at(manager, 5, 5), at(manager, 6, 6)));
        assertTrue(recorder.entered.isEmpty(), "a move inside the same regions crosses nothing");

        assertNull(session.uwgTestMoveTo(null, null, null, MoveType.MOVE, at(manager, 5, 5), at(manager, 20, 5)));
        assertNull(session.uwgTestMoveTo(null, null, null, MoveType.MOVE, at(manager, 20, 5), at(manager, 40, 40)));

        assertEquals(List.of(Set.of("b"), Set.of()), recorder.entered);
        assertEquals(List.of(Set.of("a"), Set.of("b")), recorder.exited);
    }

    @Test
    void onlyAHandlerThatTestsMovesTurnsOffTheShortcut() {
        final Session session = new Session(null);
        final boolean before = Session.uwgAnyTestsMoves();

        session.register(new Recorder(session));
        assertEquals(before, Session.uwgAnyTestsMoves(), "a crossing-only handler leaves the shortcut on");

        session.register(new Mover(session));
        assertTrue(Session.uwgAnyTestsMoves());
    }
}
