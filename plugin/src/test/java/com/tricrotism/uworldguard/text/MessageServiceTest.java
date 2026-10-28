package com.tricrotism.uworldguard.text;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A settings edit that submits the current value does not save messages.yml, so a hand edit made
 * since the last reload survives it. A real change is still saved.
 */
class MessageServiceTest {

    private ServerMock server;
    private PluginMock plugin;
    private File file;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uWorldGuard");
        final File dataFolder = plugin.getDataFolder();
        assertTrue(dataFolder.exists() || dataFolder.mkdirs());
        file = new File(dataFolder, "messages.yml");
        write(3, "<red>No.");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void write(final long cooldown, final String denial) throws IOException {
        Files.writeString(file.toPath(), "cooldown-seconds: " + cooldown + "\nmessages:\n  no-permission: \""
            + denial + "\"\n", StandardCharsets.UTF_8);
    }

    private YamlConfiguration onDisk() {
        server.getScheduler().waitAsyncTasksFinished();
        return YamlConfiguration.loadConfiguration(file);
    }

    @Test
    void anUnchangedMessageKeepsAHandEdit() throws IOException {
        final MessageService messages = new MessageService(plugin);
        write(3, "<red>Edited.");

        messages.setMessage("no-permission", "<red>No.");

        assertEquals("<red>Edited.", onDisk().getString("messages.no-permission"));
    }

    @Test
    void anUnchangedCooldownKeepsAHandEdit() throws IOException {
        final MessageService messages = new MessageService(plugin);
        write(7, "<red>No.");

        messages.setCooldownSeconds(3L);

        assertEquals(7L, onDisk().getLong("cooldown-seconds"));
    }

    @Test
    void aChangedValueIsStillSaved() {
        final MessageService messages = new MessageService(plugin);

        messages.setMessage("no-permission", "<red>Changed.");
        messages.setCooldownSeconds(4L);

        final YamlConfiguration saved = onDisk();
        assertEquals("<red>Changed.", saved.getString("messages.no-permission"));
        assertEquals(4L, saved.getLong("cooldown-seconds"));
        assertEquals("<red>Changed.", messages.raw("no-permission"));
        assertEquals(4L, messages.cooldownSeconds());
    }
}
