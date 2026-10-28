package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.region.ProtectedCuboidRegion;
import com.tricrotism.uworldguard.region.RegionContainerImpl;
import com.tricrotism.uworldguard.region.RegionManager;
import com.tricrotism.uworldguard.storage.RegionStore;
import com.tricrotism.uworldguard.text.MessageService;
import com.tricrotism.uworldguard.util.BlockVector3;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A death in a region that keeps both inventory and experience looks the victim's regions up once.
 */
class DeathListenerTest {

    private static final RegionStore NO_STORE = new RegionStore() {
        @Override
        public void load(final String worldName, final RegionManager manager) {}

        @Override
        public void save(final String worldName, final RegionManager manager) {}
    };

    private ServerMock server;
    private WorldMock world;
    private DeathListener listener;
    private ProtectedCuboidRegion arena;
    private int lookups;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        final PluginMock plugin = MockBukkit.createMockPlugin("uWorldGuard");
        world = server.addSimpleWorld("world");
        final File dataFolder = plugin.getDataFolder();
        assertTrue(dataFolder.exists() || dataFolder.mkdirs());
        Files.writeString(new File(dataFolder, "messages.yml").toPath(),
            "cooldown-seconds: 0\nmessages:\n  no-permission: \"<red>No.\"\n", StandardCharsets.UTF_8);

        final RegionContainerImpl container = new RegionContainerImpl(plugin, NO_STORE);
        container.loadAll();
        listener = new DeathListener(container.createQuery(), new MessageService(plugin));

        final RegionManager manager = container.get(world);
        assertNotNull(manager);
        arena = new ProtectedCuboidRegion("arena", BlockVector3.at(0, 0, 0), BlockVector3.at(32, 255, 32));
        manager.addRegion(arena);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bothKeepFlagsComeFromOneLookup() {
        arena.setFlag(Flags.KEEP_INVENTORY, true);
        arena.setFlag(Flags.KEEP_EXP, true);
        // a lookup reads the victim's x once
        final PlayerMock victim = new PlayerMock(server, "victim", UUID.randomUUID()) {
            @Override
            public double getX() {
                lookups++;
                return super.getX();
            }
        };
        server.addPlayer(victim);
        victim.teleport(new Location(world, 16, 64, 16));
        final List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.DIAMOND)));
        final PlayerDeathEvent event = new PlayerDeathEvent(victim, DamageSource.builder(DamageType.GENERIC).build(),
            drops, 10, null, true);
        lookups = 0;

        listener.onDeath(event);

        assertTrue(event.getKeepInventory());
        assertTrue(event.getDrops().isEmpty());
        assertTrue(event.getKeepLevel());
        assertEquals(0, event.getDroppedExp());
        assertEquals(1, lookups, "keep-inventory and keep-exp read the same region set");
    }
}
