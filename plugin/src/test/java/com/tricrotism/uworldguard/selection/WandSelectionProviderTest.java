package com.tricrotism.uworldguard.selection;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A held wand click repeats the interact event on the same block. The corner is reported once, and
 * the block stays protected on every repeat.
 */
class WandSelectionProviderTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private WandSelectionProvider wand;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        final PluginMock plugin = MockBukkit.createMockPlugin("uWorldGuard");
        world = server.addSimpleWorld("world");
        player = server.addPlayer();
        player.addAttachment(plugin, "uworldguard.region.define", true);
        wand = new WandSelectionProvider(Material.WOODEN_AXE);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerInteractEvent click(final Action action, final int x) {
        final PlayerInteractEvent event = new PlayerInteractEvent(player, action,
            new ItemStack(Material.WOODEN_AXE), world.getBlockAt(x, 64, 0), BlockFace.UP, EquipmentSlot.HAND);
        wand.onInteract(event);
        return event;
    }

    private String next() {
        final Component message = player.nextComponentMessage();
        assertNotNull(message, "the corner is reported");
        return PlainTextComponentSerializer.plainText().serialize(message);
    }

    @Test
    void aHeldClickOnTheSameBlockReportsTheCornerOnce() {
        assertEquals(Event.Result.DENY, click(Action.LEFT_CLICK_BLOCK, 0).useInteractedBlock());
        assertEquals("Set first position to 0, 64, 0.", next());
        assertEquals(Event.Result.DENY, click(Action.LEFT_CLICK_BLOCK, 0).useInteractedBlock(),
            "the block stays protected");
        assertNull(player.nextComponentMessage(), "an unchanged first corner is not reported again");

        assertEquals(Event.Result.DENY, click(Action.RIGHT_CLICK_BLOCK, 3).useInteractedBlock());
        assertEquals("Set second position to 3, 64, 0.", next());
        assertEquals(Event.Result.DENY, click(Action.RIGHT_CLICK_BLOCK, 3).useInteractedBlock(),
            "the block stays protected");
        assertNull(player.nextComponentMessage(), "an unchanged second corner is not reported again");
    }

    @Test
    void movingACornerReportsAndStoresIt() {
        click(Action.LEFT_CLICK_BLOCK, 0);
        assertEquals("Set first position to 0, 64, 0.", next());
        click(Action.LEFT_CLICK_BLOCK, 5);
        assertEquals("Set first position to 5, 64, 0.", next());
        click(Action.RIGHT_CLICK_BLOCK, 9);
        assertEquals("Set second position to 9, 64, 0.", next());

        final Selection selection = wand.getSelection(player);
        assertNotNull(selection);
        assertEquals(5, selection.min().x());
        assertEquals(9, selection.max().x());
    }
}
