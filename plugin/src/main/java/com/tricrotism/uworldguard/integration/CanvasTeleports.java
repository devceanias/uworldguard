package com.tricrotism.uworldguard.integration;

import com.tricrotism.uworldguard.listeners.MovementListener;
import com.tricrotism.uworldguard.listeners.PlayerStateListener;
import com.tricrotism.uworldguard.listeners.TravelListener;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canvas moves players with its own async teleport and never fires {@link PlayerTeleportEvent} or
 * {@link PlayerPortalEvent}: not for ender pearls, chorus fruit, {@code teleportAsync}, {@code /tp}
 * or portals. It fires {@code EntityTeleportAsyncEvent} and {@code EntityPortalAsyncEvent} before,
 * and their {@code Post} forms after, so this hands those to the handlers Paper's events reach.
 *
 * <p>Paper never fires the Canvas events and Canvas never fires Paper's for these, so nothing is
 * judged twice.
 */
@NullMarked
public final class CanvasTeleports {

    private static final String EVENTS = "io.canvasmc.canvas.event.";

    private CanvasTeleports() {}

    /**
     * Registers the bridge when the server is Canvas, returning whether it did. Bukkit drops it with
     * the plugin's other listeners on disable.
     */
    public static boolean register(
        final Plugin plugin, final PlayerStateListener state, final MovementListener movement, final TravelListener travel
    ) {
        final boolean canvas = register(plugin, state, movement, travel, EVENTS + "EntityTeleportAsyncEvent",
            EVENTS + "EntityPostTeleportAsyncEvent", EVENTS + "EntityPortalAsyncEvent", EVENTS + "EntityPostPortalAsyncEvent");
        if (canvas) {
            plugin.getLogger().info("Canvas detected: teleports and portals are judged from its async events.");
        }
        return canvas;
    }

    static boolean register(final Plugin plugin, final PlayerStateListener state, final MovementListener movement, final TravelListener travel, final String teleportName, final String teleportedName, final String portalName, final String portalledName) {
        final Reader teleport;
        final Reader teleported;
        final Reader portal;
        final Class<? extends Event> portalled;
        try {
            teleport = new Reader(Class.forName(teleportName).asSubclass(Event.class), "getFrom", "getTo", "getCause");
            teleported = new Reader(Class.forName(teleportedName).asSubclass(Event.class), "getFrom", "getTo", "getCause");
            portal = new Reader(Class.forName(portalName).asSubclass(Event.class), "getTo");
            portalled = Class.forName(portalledName).asSubclass(Event.class);
        } catch (final ReflectiveOperationException e) {
            return false;
        }
        final Listener owner = new Listener() {};
        final Map<UUID, Location> portalOrigins = new ConcurrentHashMap<>();
        final var events = plugin.getServer().getPluginManager();

        events.registerEvent(teleport.type, owner, EventPriority.HIGH, (listener, event) -> {
            if (!teleport.type.isInstance(event) || !(((EntityEvent) event).getEntity() instanceof Player player)) {
                return;
            }
            final PlayerTeleportEvent mirror = new PlayerTeleportEvent(player, (Location) teleport.read(event, 0),
                (Location) teleport.read(event, 1), (PlayerTeleportEvent.TeleportCause) teleport.read(event, 2));
            state.onTeleport(mirror);
            if (!mirror.isCancelled()) {
                movement.onTeleport(mirror);
            }
            if (mirror.isCancelled()) {
                ((Cancellable) event).setCancelled(true);
            }
        }, plugin, true);
        events.registerEvent(teleported.type, owner, EventPriority.MONITOR, (listener, event) -> {
            if (teleported.type.isInstance(event) && ((EntityEvent) event).getEntity() instanceof Player player) {
                movement.onTeleported(new PlayerTeleportEvent(player, (Location) teleported.read(event, 0),
                    (Location) teleported.read(event, 1), (PlayerTeleportEvent.TeleportCause) teleported.read(event, 2)));
            }
        }, plugin);

        events.registerEvent(portal.type, owner, EventPriority.HIGH, (listener, event) -> {
            if (!portal.type.isInstance(event)) {
                return;
            }
            final Entity entity = ((EntityEvent) event).getEntity();
            if (!(entity instanceof Player) && entity.isEmpty()) {
                return;
            }
            final Location from = entity.getLocation();
            final Location to = from.clone();
            if (portal.read(event, 0) instanceof World target) {
                to.setWorld(target);
            }
            final boolean refused;
            if (entity instanceof Player player) {
                final PlayerPortalEvent mirror = new PlayerPortalEvent(player, from, to);
                travel.onPortal(mirror);
                if (!mirror.isCancelled()) {
                    state.onPortal(mirror);
                }
                refused = mirror.isCancelled();
                if (!refused) {
                    portalOrigins.put(player.getUniqueId(), from);
                }
            } else {
                final EntityPortalEvent mirror = new EntityPortalEvent(entity, from, to);
                travel.onVehiclePortal(mirror);
                refused = mirror.isCancelled();
            }
            if (refused) {
                ((Cancellable) event).setCancelled(true);
            }
        }, plugin, true);
        events.registerEvent(portalled, owner, EventPriority.MONITOR, (listener, event) -> {
            if (!portalled.isInstance(event) || !(((EntityEvent) event).getEntity() instanceof Player player)) {
                return;
            }
            final Location origin = portalOrigins.remove(player.getUniqueId());
            if (origin == null) {
                return;
            }
            final PlayerPortalEvent mirror = new PlayerPortalEvent(player, origin, player.getLocation());
            movement.onPortal(mirror);
            if (mirror.isCancelled()) {
                player.teleportAsync(origin);
                return;
            }
            movement.onPortalled(mirror);
        }, plugin);
        events.registerEvent(PlayerQuitEvent.class, owner, EventPriority.MONITOR,
            (listener, event) -> {
                if (event instanceof PlayerQuitEvent quit) {
                    portalOrigins.remove(quit.getPlayer().getUniqueId());
                }
            }, plugin);
        return true;
    }

    /**
     * Reads a Canvas event's getters by reflection, as uWorldGuard builds against Paper's API.
     */
    private static final class Reader {

        private final Class<? extends Event> type;
        private final Method[] getters;

        Reader(final Class<? extends Event> type, final String... names) throws NoSuchMethodException {
            this.type = type;
            this.getters = new Method[names.length];
            for (int i = 0; i < names.length; i++) {
                getters[i] = type.getMethod(names[i]);
            }
        }

        Object read(final Event event, final int getter) {
            try {
                return getters[getter].invoke(event);
            } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("could not read " + type.getName(), e);
            }
        }
    }
}
