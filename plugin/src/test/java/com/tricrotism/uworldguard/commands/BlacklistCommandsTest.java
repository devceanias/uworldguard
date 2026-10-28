package com.tricrotism.uworldguard.commands;

import com.tricrotism.uworldguard.blacklist.Blacklist;
import com.tricrotism.uworldguard.blacklist.BlacklistService;
import com.tricrotism.uworldguard.text.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.incendo.cloud.paper.util.sender.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code /uwg blacklist}: edits land in the loaded config, apply to the live blacklist at once, and
 * reach config.yml on disk. Bad input is refused with a message and changes nothing.
 */
class BlacklistCommandsTest {

    private ServerMock server;
    private PluginMock plugin;
    private WorldMock world;
    private BlacklistService service;
    private BlacklistCommands commands;
    private PlayerMock admin;
    private Source sender;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uWorldGuard");
        world = server.addSimpleWorld("world");
        final File dataFolder = plugin.getDataFolder();
        assertTrue(dataFolder.exists() || dataFolder.mkdirs());
        Files.writeString(new File(dataFolder, "messages.yml").toPath(),
            "cooldown-seconds: 0\nmessages:\n  no-permission: \"<red>No.\"\n", StandardCharsets.UTF_8);

        service = new BlacklistService(plugin, new MessageService(plugin));
        commands = new BlacklistCommands(plugin, service);
        admin = server.addPlayer();
        sender = (Source) Proxy.newProxyInstance(BlacklistCommandsTest.class.getClassLoader(),
            new Class<?>[]{Source.class}, (proxy, method, args) -> switch (method.getName()) {
                case "source" -> admin;
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private String reply() {
        final Component message = admin.nextComponentMessage();
        assertNotNull(message, "the command answers");
        return PlainTextComponentSerializer.plainText().serialize(message);
    }

    private List<Map<?, ?>> entries() {
        return plugin.getConfig().getMapList("blacklist.entries");
    }

    private void addTntEntry() {
        commands.add(sender, "global", "tnt, lava_bucket");
        assertTrue(reply().startsWith("Added entry #1."));
        commands.set(sender, "global", 1, "on-place", "deny,tell");
        assertEquals("Set on-place on entry #1.", reply());
    }

    @Test
    void addAndSetApplyAtOnceAndLandInTheConfig() {
        addTntEntry();

        final Blacklist list = service.forWorld(world);
        assertNotNull(list);
        assertFalse(service.allows(list, Blacklist.Rule.PLACE, admin, Material.TNT, world, 0, 64, 0, true));
        assertTrue(service.allows(list, Blacklist.Rule.PLACE, admin, Material.STONE, world, 0, 64, 0, true));
        assertEquals(List.of(Map.of("items", List.of("tnt", "lava_bucket"), "on-place", List.of("deny", "tell"))),
            entries());
    }

    @Test
    void anUnknownItemIsRefusedAndChangesNothing() {
        commands.add(sender, "global", "tnt, not_a_thing");

        assertEquals("Unknown item: not_a_thing.", reply());
        assertTrue(entries().isEmpty());
        assertNull(service.forWorld(world));
    }

    @Test
    void anUnknownActionIsRefusedAndChangesNothing() {
        addTntEntry();
        final List<Map<?, ?>> before = entries();

        commands.set(sender, "global", 1, "on-break", "deny,explode");

        assertTrue(reply().startsWith("Unknown action explode."));
        assertEquals(before, entries());
    }

    @Test
    void anEntryNumberOutOfRangeIsRefusedAndChangesNothing() {
        addTntEntry();
        final List<Map<?, ?>> before = entries();

        commands.set(sender, "global", 2, "on-break", "deny");
        assertTrue(reply().startsWith("No such entry."));
        commands.remove(sender, "global", 0);
        assertTrue(reply().startsWith("No such entry."));

        assertEquals(before, entries());
    }

    @Test
    void removeDropsTheEntry() {
        addTntEntry();

        commands.remove(sender, "global", 1);

        assertEquals("Removed entry #1.", reply());
        assertTrue(entries().isEmpty());
        assertNull(service.forWorld(world));
    }

    @Test
    void whitelistFlipsTheMode() {
        addTntEntry();

        commands.whitelist(sender, "global", true);

        assertEquals("Whitelist mode is on for global.", reply());
        assertTrue(plugin.getConfig().getBoolean("blacklist.use-as-whitelist"));
        final Blacklist list = service.forWorld(world);
        assertNotNull(list);
        assertTrue(list.whitelist());
    }

    /**
     * Waits for pending writes, then adds a line to config.yml by hand. A later write of the loaded
     * config drops it.
     */
    private Path handEditConfig() throws Exception {
        server.getScheduler().waitAsyncTasksFinished();
        final Path file = new File(plugin.getDataFolder(), "config.yml").toPath();
        Files.writeString(file, "# hand edit\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        return file;
    }

    private void assertNotRewritten(final Path file) throws Exception {
        server.getScheduler().waitAsyncTasksFinished();
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("# hand edit"),
            "config.yml is not rewritten");
    }

    @Test
    void whitelistWithTheStoredValueChangesNothing() throws Exception {
        addTntEntry();
        commands.whitelist(sender, "global", true);
        assertEquals("Whitelist mode is on for global.", reply());
        final Blacklist list = service.forWorld(world);
        final Path file = handEditConfig();

        commands.whitelist(sender, "global", true);

        assertEquals("Whitelist mode is on for global.", reply());
        assertSame(list, service.forWorld(world), "the blacklist is not rebuilt");
        assertNotRewritten(file);
    }

    @Test
    void settingAnOptionToWhatItHoldsChangesNothing() throws Exception {
        addTntEntry();
        final Blacklist list = service.forWorld(world);
        final Path file = handEditConfig();

        commands.set(sender, "global", 1, "on-place", "deny,tell");
        assertEquals("Set on-place on entry #1.", reply());
        commands.set(sender, "global", 1, "on-break", null);
        assertEquals("Cleared on-break on entry #1.", reply());

        assertSame(list, service.forWorld(world), "the blacklist is not rebuilt");
        assertNotRewritten(file);
    }

    @Test
    void theEditIsWrittenToConfigYml() throws Exception {
        addTntEntry();

        server.getScheduler().waitAsyncTasksFinished();

        final String text = Files.readString(new File(plugin.getDataFolder(), "config.yml").toPath(),
            StandardCharsets.UTF_8);
        assertTrue(text.contains("lava_bucket"), text);
        assertTrue(text.contains("on-place"), text);
    }
}
