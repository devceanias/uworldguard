package com.tricrotism.uworldguard.integration;

import com.sk89q.worldedit.extent.NullExtent;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.handler.FlagValueChangeHandler;
import com.sk89q.worldguard.session.handler.Handler;
import com.tricrotism.uworldguard.region.ProtectedCuboidRegion;
import com.tricrotism.uworldguard.region.RegionManager;
import com.tricrotism.uworldguard.util.BlockVector3;
import com.tricrotism.uworldguard.wgcompat.WrappedRegionSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A flag value handler hears only real transitions and keeps a value only once the move is allowed,
 * and a session with nothing to tick does no lookup on tick.
 *
 * <p>{@code SessionBridge.tick}, with its {@code uwgTicks} guard and coordinate mapping, is not covered:
 * it goes through {@code PlayerWrapping}, which needs worldedit-bukkit on the test classpath.
 */
class SessionHandlerTest {

    private static final Location FROM = new Location(new NullExtent(), 20, 64, 5);

    private static final class Greeting extends FlagValueChangeHandler<String> {
        final List<String> values = new ArrayList<>();
        final List<String> absent = new ArrayList<>();
        boolean refuse;

        Greeting(final Session session) {
            super(session, Flags.GREET_MESSAGE);
        }

        @Override
        protected void onInitialValue(final LocalPlayer player, final ApplicableRegionSet set, final String value) {
        }

        @Override
        protected boolean onSetValue(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet, final String currentValue, final String lastValue, final MoveType moveType) {
            values.add(currentValue);
            return !refuse;
        }

        @Override
        protected boolean onAbsentValue(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet, final String lastValue, final MoveType moveType) {
            absent.add(lastValue);
            return true;
        }
    }

    private static final class Ticker extends Handler {
        ApplicableRegionSet seen;

        Ticker(final Session session) {
            super(session);
        }

        @Override
        public void tick(final LocalPlayer player, final ApplicableRegionSet set) {
            seen = set;
        }
    }

    /**
     * Plots a and b leave the greeting unset, plot c sets it.
     */
    private static RegionManager plots() {
        final RegionManager manager = new RegionManager();
        manager.addRegion(new ProtectedCuboidRegion("a", BlockVector3.at(0, 0, 0), BlockVector3.at(15, 255, 15)));
        manager.addRegion(new ProtectedCuboidRegion("b", BlockVector3.at(16, 0, 0), BlockVector3.at(31, 255, 15)));
        final ProtectedCuboidRegion c =
            new ProtectedCuboidRegion("c", BlockVector3.at(32, 0, 0), BlockVector3.at(47, 255, 15));
        c.setFlag(com.tricrotism.uworldguard.flags.Flags.GREETING, "hello");
        manager.addRegion(c);
        return manager;
    }

    private static WrappedRegionSet at(final RegionManager manager, final int x, final int z) {
        return new WrappedRegionSet(manager.getApplicableRegions(x, 64, z), manager);
    }

    @Test
    void crossingBetweenTwoPlotsWithoutTheFlagReportsNothingGone() {
        final RegionManager manager = plots();
        final Session session = new Session(null);
        final Greeting handler = new Greeting(session);
        session.register(handler);
        handler.initialize(null, null, at(manager, 5, 5));

        assertNull(session.uwgTestMoveTo(null, FROM, null, MoveType.MOVE, at(manager, 5, 5), at(manager, 20, 5)));
        assertEquals(List.of(), handler.absent, "neither plot sets the flag, so nothing went away");

        assertNull(session.uwgTestMoveTo(null, FROM, null, MoveType.MOVE, at(manager, 20, 5), at(manager, 40, 5)));
        session.uwgTestMoveTo(null, FROM, null, MoveType.MOVE, at(manager, 40, 5), at(manager, 20, 5));
        assertEquals(List.of("hello"), handler.values);
        assertEquals(List.of("hello"), handler.absent, "leaving the plot that sets it is still reported");
    }

    @Test
    void aHandlerThatRefusesEntryIsAskedAgainOnTheNextAttempt() {
        final RegionManager manager = plots();
        final Session session = new Session(null);
        final Greeting handler = new Greeting(session);
        handler.refuse = true;
        session.register(handler);
        handler.initialize(null, null, at(manager, 20, 5));

        assertSame(FROM, session.uwgTestMoveTo(null, FROM, null, MoveType.MOVE, at(manager, 20, 5), at(manager, 40, 5)),
            "the refusal sends the player back");
        assertSame(FROM, session.uwgTestMoveTo(null, FROM, null, MoveType.MOVE, at(manager, 20, 5), at(manager, 40, 5)),
            "the second attempt is refused too");

        assertEquals(List.of("hello", "hello"), handler.values, "a refused value is never kept as the player's");
    }

    @Test
    void aSessionWhoseHandlersDoNotTickLooksNothingUp() {
        final Session session = new Session(null);
        session.register(new Greeting(session));

        assertFalse(session.uwgTicks());
        assertDoesNotThrow(() -> session.tick(null), "with nothing to tick the player's location is never read");

        final Ticker ticker = new Ticker(session);
        session.register(ticker);
        final WrappedRegionSet set = at(plots(), 5, 5);
        session.uwgTick(null, set);

        assertTrue(session.uwgTicks());
        assertSame(set, ticker.seen, "a ticking handler gets the set the caller resolved");
    }
}
