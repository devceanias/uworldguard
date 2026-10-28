package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.region.ProtectedCuboidRegion;
import com.tricrotism.uworldguard.region.RegionContainerImpl;
import com.tricrotism.uworldguard.region.RegionManager;
import com.tricrotism.uworldguard.storage.RegionStore;
import com.tricrotism.uworldguard.util.BlockVector3;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.EnderCrystalMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A crystal popped in a crystal arena next to a sword arena that denies other-explosion. The flag is
 * judged where the victim stands, as WorldGuard does, so the blast stops at the border.
 */
class CrystalDamageTest {

    private static final RegionStore NO_STORE = new RegionStore() {
        @Override
        public void load(final String worldName, final RegionManager manager) {}

        @Override
        public void save(final String worldName, final RegionManager manager) {}
    };

    private ServerMock server;
    private WorldMock world;
    private EntityListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        final PluginMock plugin = MockBukkit.createMockPlugin("uWorldGuard");
        world = server.addSimpleWorld("world");
        final RegionContainerImpl container = new RegionContainerImpl(plugin, NO_STORE);
        container.loadAll();
        listener = new EntityListener(container, container.createQuery());

        final RegionManager manager = container.get(world);
        assertNotNull(manager);
        final ProtectedCuboidRegion swordpvp = new ProtectedCuboidRegion("swordpvp",
            BlockVector3.at(0, 0, 0), BlockVector3.at(15, 255, 15));
        swordpvp.setFlag(Flags.PVP, State.ALLOW);
        swordpvp.setFlag(Flags.OTHER_EXPLOSION, State.DENY);
        manager.addRegion(swordpvp);
        manager.addRegion(new ProtectedCuboidRegion("crystalpvp",
            BlockVector3.at(16, 0, 0), BlockVector3.at(31, 255, 15)));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private boolean blast(final double victimX) {
        final EnderCrystalMock crystal = new EnderCrystalMock(server, UUID.randomUUID());
        crystal.teleport(new Location(world, 17.5, 64, 8.5));
        final Player victim = server.addPlayer();
        victim.teleport(new Location(world, victimX, 64, 8.5));
        final EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(crystal, victim,
            EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, DamageSource.builder(DamageType.EXPLOSION).build(), 6.0);
        listener.onEntityDamage(event);
        return !event.isCancelled();
    }

    @Test
    void aCrystalAcrossTheBorderCannotHurtASwordPlayer() {
        assertFalse(blast(15.5));
    }

    @Test
    void aCrystalStillHurtsInsideTheCrystalArena() {
        assertTrue(blast(19.5));
    }
}
