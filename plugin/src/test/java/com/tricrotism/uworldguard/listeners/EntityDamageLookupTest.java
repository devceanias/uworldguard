package com.tricrotism.uworldguard.listeners;

import com.destroystokyo.paper.event.entity.EntityZapEvent;
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
import org.mockbukkit.mockbukkit.entity.*;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How often one hit looks up the victim's regions. A lookup reads the victim's x once, so the victims
 * here count those reads.
 */
class EntityDamageLookupTest {

    private static final RegionStore NO_STORE = new RegionStore() {
        @Override
        public void load(final String worldName, final RegionManager manager) {}

        @Override
        public void save(final String worldName, final RegionManager manager) {}
    };

    private ServerMock server;
    private WorldMock world;
    private EntityListener listener;
    private ProtectedCuboidRegion claim;
    private int lookups;

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
        claim = new ProtectedCuboidRegion("claim", BlockVector3.at(0, 0, 0), BlockVector3.at(32, 255, 32));
        manager.addRegion(claim);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private boolean hit(final Entity damager, final Entity victim) {
        victim.teleport(new Location(world, 16, 64, 16));
        final EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(damager, victim,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK, DamageSource.builder(DamageType.MOB_ATTACK).build(), 1.0);
        lookups = 0;
        listener.onEntityDamage(event);
        return !event.isCancelled();
    }

    @Test
    void aMobNoFlagGuardsIsNeverLookedUp() {
        claim.setFlag(Flags.ALLOW_ENTITY_DAMAGE, Set.of(EntityType.BAT));
        final ZombieMock zombie = new ZombieMock(server, UUID.randomUUID()) {
            @Override
            public double getX() {
                lookups++;
                return super.getX();
            }
        };

        assertTrue(hit(server.addPlayer(), zombie));
        assertEquals(0, lookups, "anyone may fight a hostile mob, so allow-entity-damage decides nothing");
    }

    @Test
    void aGuardedAnimalIsLookedUpOnce() {
        claim.setFlag(Flags.ALLOW_ENTITY_DAMAGE, Set.of(EntityType.BAT));
        final CowMock cow = new CowMock(server, UUID.randomUUID()) {
            @Override
            public double getX() {
                lookups++;
                return super.getX();
            }
        };

        assertFalse(hit(server.addPlayer(), cow), "a stranger still cannot hurt an unlisted cow");
        assertEquals(1, lookups, "allow-entity-damage and the build test share one lookup");
    }

    @Test
    void aCreeperHitOnAPlayerIsLookedUpOnce() {
        claim.setFlag(Flags.CREEPER_EXPLOSION, State.ALLOW);
        claim.setFlag(Flags.MOB_DAMAGE, State.DENY);
        final PlayerMock victim = new PlayerMock(server, "victim", UUID.randomUUID()) {
            @Override
            public double getX() {
                lookups++;
                return super.getX();
            }
        };
        server.addPlayer(victim);

        assertFalse(hit(new CreeperMock(server, UUID.randomUUID()), victim), "mob-damage still refuses the hit");
        assertEquals(1, lookups, "creeper-explosion and mob-damage share one lookup");
    }

    @Test
    void aZappedPigIsLookedUpOnce() {
        claim.setFlag(Flags.ENTITY_TRANSFORM, State.ALLOW);
        final PigMock pig = new PigMock(server, UUID.randomUUID()) {
            @Override
            public double getX() {
                lookups++;
                return super.getX();
            }
        };
        pig.teleport(new Location(world, 16, 64, 16));
        final LightningStrikeMock bolt = new LightningStrikeMock(server, UUID.randomUUID());
        bolt.teleport(new Location(world, 16, 64, 16));
        final EntityZapEvent event = new EntityZapEvent(pig, bolt, new PigZombieMock(server, UUID.randomUUID()));
        lookups = 0;
        listener.onZap(event);

        assertFalse(event.isCancelled(), "a bolt striking inside the claim may convert the pig");
        assertEquals(1, lookups, "entity-transform and damage-animals share one lookup");
    }
}
