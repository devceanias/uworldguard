package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.blacklist.Blacklist;
import com.tricrotism.uworldguard.blacklist.Blacklist.Rule;
import com.tricrotism.uworldguard.blacklist.BlacklistService;
import com.tricrotism.uworldguard.config.EventGate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Feeds player actions to the world's {@link Blacklist}, mapped onto Bukkit events the way WorldGuard
 * maps its own. Every handler starts with the world's list and returns when there is none, so a
 * server that lists nothing pays one map lookup per event. Coordinates are only read once the item
 * turns out to be listed.
 */
@NullMarked
public final class BlacklistListener implements Listener {

    private final BlacklistService blacklist;

    public BlacklistListener(final BlacklistService blacklist) {
        this.blacklist = blacklist;
    }

    /**
     * Breaking checks the block ({@code on-break}), then what the player broke it with
     * ({@code on-destroy-with}).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent event) {
        final Block block = event.getBlock();
        final Blacklist list = blacklist.forWorld(block.getWorld());
        if (list == null || EventGate.disabled(event)) {
            return;
        }
        final Player player = event.getPlayer();
        if (refused(list, Rule.BREAK, player, block.getType(), block)) {
            event.setCancelled(true);
            return;
        }
        if (!list.uses(Rule.DESTROY_WITH)) {
            return;
        }
        final Material tool = player.getInventory().getItemInMainHand().getType();
        if (!tool.isAir() && refused(list, Rule.DESTROY_WITH, player, tool, block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event) {
        final Block block = event.getBlock();
        final Blacklist list = blacklist.forWorld(block.getWorld());
        if (list != null && !EventGate.disabled(event)
            && refused(list, Rule.PLACE, event.getPlayer(), block.getType(), block)) {
            event.setCancelled(true);
        }
    }

    /**
     * Pouring a bucket places the fluid without a {@link BlockPlaceEvent}, so it is checked as
     * placing that fluid. A fish bucket pours water.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(final PlayerBucketEmptyEvent event) {
        final Block block = event.getBlock();
        final Blacklist list = blacklist.forWorld(block.getWorld());
        if (list == null || EventGate.disabled(event)) {
            return;
        }
        final Material bucket = event.getBucket();
        final Material fluid = bucket == Material.LAVA_BUCKET ? Material.LAVA
            : bucket == Material.POWDER_SNOW_BUCKET ? Material.POWDER_SNOW : Material.WATER;
        if (refused(list, Rule.PLACE, event.getPlayer(), fluid, block)) {
            event.setCancelled(true);
        }
    }

    /**
     * Right-clicking or stepping on a block is {@code on-interact}. Right-clicking with an item is
     * {@code on-use}, and {@code on-equip} too for armour, which a right-click puts on. Block items
     * are left to {@code on-place}.
     *
     * <p>Not {@code ignoreCancelled}: a click at air arrives already cancelled, and the item in hand
     * is still used.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(final PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        final Blacklist list = blacklist.forWorld(player.getWorld());
        if (list == null || EventGate.disabled(event)) {
            return;
        }
        final Action action = event.getAction();
        final Block clicked = event.getClickedBlock();
        if (clicked != null && event.useInteractedBlock() != Event.Result.DENY
            && (action == Action.RIGHT_CLICK_BLOCK || action == Action.PHYSICAL)
            && refused(list, Rule.INTERACT, player, clicked.getType(), clicked)) {
            if (action == Action.PHYSICAL) {
                event.setCancelled(true);
                return;
            }
            event.setUseInteractedBlock(Event.Result.DENY);
        }
        final ItemStack item = event.getItem();
        if (item == null || event.useItemInHand() == Event.Result.DENY
            || (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        final Material type = item.getType();
        if (type.isBlock()) {
            return;
        }
        if (refused(list, Rule.USE, player, type, false)
            || (list.uses(Rule.EQUIP) && list.entriesFor(type) != null && type.getEquipmentSlot().isArmor()
            && refused(list, Rule.EQUIP, player, type, false))) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(final PlayerItemConsumeEvent event) {
        final Player player = event.getPlayer();
        final Blacklist list = blacklist.forWorld(player.getWorld());
        if (list != null && !EventGate.disabled(event)
            && refused(list, Rule.USE, player, event.getItem().getType(), false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        final Player player = event.getPlayer();
        final Blacklist list = blacklist.forWorld(player.getWorld());
        if (list != null && list.uses(Rule.DROP) && !EventGate.disabled(event)
            && refused(list, Rule.DROP, player, event.getItemDrop().getItemStack().getType(), false)) {
            event.setCancelled(true);
        }
    }

    /**
     * Silent, as in WorldGuard: an item on the ground is offered to the player again every tick, so
     * telling or logging each attempt would flood both.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final Blacklist list = blacklist.forWorld(player.getWorld());
        if (list != null && list.uses(Rule.ACQUIRE) && !EventGate.disabled(event)
            && refused(list, Rule.ACQUIRE, player, event.getItem().getItemStack().getType(), true)) {
            event.setCancelled(true);
        }
    }

    /**
     * Taking an item out of an inventory is {@code on-acquire}, and as in WorldGuard a refused item
     * clicked in the player's own inventory is taken away. Putting armour on through the inventory is
     * {@code on-equip}. A creative-mode pick arrives here too, with the item on the cursor.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final Blacklist list = blacklist.forWorld(player.getWorld());
        if (list == null || EventGate.disabled(event)) {
            return;
        }
        if (list.uses(Rule.ACQUIRE)) {
            if (event instanceof InventoryCreativeEvent) {
                final ItemStack cursor = event.getCursor();
                if (!cursor.isEmpty() && refused(list, Rule.ACQUIRE, player, cursor.getType(), false)) {
                    event.setCancelled(true);
                    return;
                }
            }
            final ItemStack item = event.getCurrentItem();
            if (item != null && !item.isEmpty() && list.entriesFor(item.getType()) != null) {
                final InventoryHolder holder = event.getInventory().getHolder(false);
                if (holder != null && refused(list, Rule.ACQUIRE, player, item.getType(), false)) {
                    event.setCancelled(true);
                    if (holder.equals(player)) {
                        event.setCurrentItem(null);
                    }
                    return;
                }
            }
        }
        if (!list.uses(Rule.EQUIP)) {
            return;
        }
        final ItemStack equipped = equipped(event);
        if (equipped != null && !equipped.isEmpty()
            && refused(list, Rule.EQUIP, player, equipped.getType(), false)) {
            event.setCancelled(true);
        }
    }

    /**
     * Dragging armor across the armor slots of the player's own inventory, raw slots 5 to 8.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        final HumanEntity who = event.getWhoClicked();
        final InventoryType top = event.getInventory().getType();
        if (!(who instanceof Player player) || (top != InventoryType.PLAYER && top != InventoryType.CRAFTING)) {
            return;
        }
        final Blacklist list = blacklist.forWorld(player.getWorld());
        if (list == null || !list.uses(Rule.EQUIP) || EventGate.disabled(event)) {
            return;
        }
        for (final int slot : event.getRawSlots()) {
            if (slot >= 5 && slot <= 8) {
                if (refused(list, Rule.EQUIP, player, event.getOldCursor().getType(), false)) {
                    event.setCancelled(true);
                }
                return;
            }
        }
    }

    /**
     * Scrolling to a refused item takes it away, as in WorldGuard, so contraband picked up before the
     * list changed cannot be held.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemHeld(final PlayerItemHeldEvent event) {
        final Player player = event.getPlayer();
        final Blacklist list = blacklist.forWorld(player.getWorld());
        if (list == null || !list.uses(Rule.ACQUIRE) || EventGate.disabled(event)) {
            return;
        }
        final PlayerInventory inventory = player.getInventory();
        final ItemStack item = inventory.getItem(event.getNewSlot());
        if (item != null && !item.isEmpty() && refused(list, Rule.ACQUIRE, player, item.getType(), false)) {
            inventory.setItem(event.getNewSlot(), null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(final BlockDispenseEvent event) {
        final Block block = event.getBlock();
        final Blacklist list = blacklist.forWorld(block.getWorld());
        if (list != null && !EventGate.disabled(event)
            && refused(list, Rule.DISPENSE, null, event.getItem().getType(), block)) {
            event.setCancelled(true);
        }
    }

    /**
     * A dispenser putting armor straight onto a player. Silent, since the player did nothing.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispenseArmor(final BlockDispenseArmorEvent event) {
        if (!(event.getTargetEntity() instanceof Player player)) {
            return;
        }
        final Blacklist list = blacklist.forWorld(player.getWorld());
        if (list != null && !EventGate.disabled(event)
            && refused(list, Rule.EQUIP, player, event.getItem().getType(), true)) {
            event.setCancelled(true);
        }
    }

    /**
     * The item a click puts into an armor slot, or {@code null} when it puts none there. WorldGuard's
     * cases: placing or swapping the cursor onto the slot, a number-key or off-hand swap into it, and
     * shift-clicking armor from the player's own inventory.
     */
    private static @Nullable ItemStack equipped(final InventoryClickEvent event) {
        final Inventory clicked = event.getClickedInventory();
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            return switch (event.getAction()) {
                case PLACE_ONE, PLACE_SOME, PLACE_ALL, SWAP_WITH_CURSOR -> event.getCursor();
                case HOTBAR_SWAP -> !(clicked instanceof PlayerInventory inventory) ? null
                    : event.getClick() == ClickType.SWAP_OFFHAND ? inventory.getItemInOffHand()
                    : inventory.getItem(event.getHotbarButton());
                default -> null;
            };
        }
        final InventoryType top = event.getView().getTopInventory().getType();
        if (clicked != null && clicked.getType() == InventoryType.PLAYER
            && (top == InventoryType.PLAYER || top == InventoryType.CRAFTING)
            && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            final ItemStack current = event.getCurrentItem();
            return current != null && current.getType().getEquipmentSlot().isArmor() ? current : null;
        }
        return null;
    }

    private boolean refused(
        final Blacklist list, final Rule rule, final @Nullable Player player, final Material item, final Block at
    ) {
        return list.uses(rule) && list.entriesFor(item) != null
            && !blacklist.allows(list, rule, player, item, at.getWorld(), at.getX(), at.getY(), at.getZ(), false);
    }

    /**
     * For item rules, which happen where the player stands.
     */
    private boolean refused(
        final Blacklist list, final Rule rule, final Player player, final Material item, final boolean silent
    ) {
        if (!list.uses(rule) || list.entriesFor(item) == null) {
            return false;
        }
        final Location at = player.getLocation();
        return !blacklist.allows(list, rule, player, item, at.getWorld(), at.getBlockX(), at.getBlockY(), at.getBlockZ(), silent);
    }
}
