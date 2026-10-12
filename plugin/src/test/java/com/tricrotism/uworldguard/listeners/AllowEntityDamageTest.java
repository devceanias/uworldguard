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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.BatMock;
import org.mockbukkit.mockbukkit.entity.CowMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A stranger hitting mobs in a claim they can't build in, with and without the type listed.
 */
class AllowEntityDamageTest {

    private static final RegionStore NO_STORE = new RegionStore() {
        @Override
        public void load(final String worldName, final RegionManager manager) {}

        @Override
        public void save(final String worldName, final RegionManager manager) {}
    };

    private ServerMock server;
    private WorldMock world;
    private RegionContainerImpl container;
    private EntityListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        final PluginMock plugin = MockBukkit.createMockPlugin("uWorldGuard");
        world = server.addSimpleWorld("world");
        container = new RegionContainerImpl(plugin, NO_STORE);
        container.loadAll();
        listener = new EntityListener(container, container.createQuery());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ProtectedCuboidRegion claim() {
        final RegionManager manager = container.get(world);
        assertNotNull(manager);
        final ProtectedCuboidRegion region = new ProtectedCuboidRegion("safezone",
            BlockVector3.at(0, 0, 0), BlockVector3.at(32, 255, 32));
        manager.addRegion(region);
        return region;
    }

    private boolean hit(final Entity victim) {
        victim.teleport(new Location(world, 16, 64, 16));
        final EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(server.addPlayer(), victim,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK, DamageSource.builder(DamageType.PLAYER_ATTACK).build(), 1.0);
        listener.onEntityDamage(event);
        return !event.isCancelled();
    }

    @Test
    void anUnlistedBatIsProtected() {
        claim();

        assertFalse(hit(new BatMock(server, UUID.randomUUID())));
    }

    @Test
    void aListedBatCanBeHit() {
        claim().setFlag(Flags.ALLOW_ENTITY_DAMAGE, Set.of(EntityType.BAT));

        assertTrue(hit(new BatMock(server, UUID.randomUUID())));
    }

    @Test
    void theListOnlyOpensItsOwnTypes() {
        claim().setFlag(Flags.ALLOW_ENTITY_DAMAGE, Set.of(EntityType.BAT));

        assertFalse(hit(new CowMock(server, UUID.randomUUID())));
    }

    @Test
    void aListedAnimalBeatsDeniedDamageAnimals() {
        final ProtectedCuboidRegion region = claim();
        region.setFlag(Flags.DAMAGE_ANIMALS, State.DENY);
        region.setFlag(Flags.ALLOW_ENTITY_DAMAGE, Set.of(EntityType.COW));

        assertTrue(hit(new CowMock(server, UUID.randomUUID())));
    }
}
