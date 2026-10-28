package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.RegionGroup;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.region.ProtectedCuboidRegion;
import com.tricrotism.uworldguard.region.RegionContainerImpl;
import com.tricrotism.uworldguard.region.RegionManager;
import com.tricrotism.uworldguard.storage.RegionStore;
import com.tricrotism.uworldguard.text.MessageService;
import com.tricrotism.uworldguard.util.BlockVector3;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerTeleportEvent;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * A priority 1 region denying {@code enderpearl} with a priority 0 claim inside it. WorldGuard tests
 * the flag where the pearl was thrown from and where it lands, both for the thrower.
 */
class EnderpearlFlagTest {

    private static final RegionStore NO_STORE = new RegionStore() {
        @Override
        public void load(final String worldName, final RegionManager manager) {}

        @Override
        public void save(final String worldName, final RegionManager manager) {}
    };

    private WorldMock world;
    private RegionManager manager;
    private PlayerStateListener listener;
    private PlayerMock player;
    private ProtectedCuboidRegion spawn;

    @BeforeEach
    void setUp() throws Exception {
        final ServerMock server = MockBukkit.mock();
        final PluginMock plugin = MockBukkit.createMockPlugin("uWorldGuard");
        world = server.addSimpleWorld("world");
        final File dataFolder = plugin.getDataFolder();
        assertTrue(dataFolder.exists() || dataFolder.mkdirs());
        Files.writeString(new File(dataFolder, "messages.yml").toPath(),
            "cooldown-seconds: 0\nmessages:\n  no-permission: \"<red>No.\"\n", StandardCharsets.UTF_8);
        final RegionContainerImpl container = new RegionContainerImpl(plugin, NO_STORE);
        container.loadAll();
        manager = container.get(world);
        assertNotNull(manager);
        listener = new PlayerStateListener(container.createQuery(), new MessageService(plugin));
        player = server.addPlayer();

        spawn = new ProtectedCuboidRegion("spawn", BlockVector3.at(-100, 0, -100), BlockVector3.at(100, 255, 100));
        spawn.setPriority(1);
        spawn.setFlag(Flags.ENDERPEARL, State.DENY);
        manager.addRegion(spawn);
        manager.addRegion(new ProtectedCuboidRegion("plot", BlockVector3.at(0, 0, 0), BlockVector3.at(20, 255, 20)));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private boolean pearl(final int fromX, final int toX) {
        final PlayerTeleportEvent event = new PlayerTeleportEvent(player,
            new Location(world, fromX + 0.5, 64, 5.5), new Location(world, toX + 0.5, 64, 5.5),
            PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        listener.onTeleport(event);
        return event.isCancelled();
    }

    @Test
    void aPearlInsideTheDeniedAreaIsRefused() {
        assertTrue(pearl(5, 10));
    }

    @Test
    void aPearlThrownOutOfTheDeniedAreaIsRefused() {
        assertTrue(pearl(5, 200));
    }

    @Test
    void aPearlOutsideTheDeniedAreaIsAllowed() {
        assertFalse(pearl(200, 210));
    }

    @Test
    void chorusFruitIsJudgedThoughItTeleportsAsAConsumeEffect() {
        spawn.setFlag(Flags.CHORUS_TELEPORT, State.DENY);
        final PlayerTeleportEvent event = new PlayerTeleportEvent(player,
            new Location(world, 5.5, 64, 5.5), new Location(world, 8.5, 64, 5.5),
            PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT);

        listener.onTeleport(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void aDenyForNonMembersSparesMembers() {
        spawn.setFlagGroup(Flags.ENDERPEARL, RegionGroup.NON_MEMBERS);
        spawn.getMembers().addPlayer(player.getUniqueId());

        assertFalse(pearl(5, 10));
    }
}
