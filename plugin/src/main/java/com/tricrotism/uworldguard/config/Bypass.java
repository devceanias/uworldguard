package com.tricrotism.uworldguard.config;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a player's region protections are currently bypassed.
 *
 * <p>Holding {@code uworldguard.bypass} allows {@code /uwg bypass} to arm protection bypass.
 * {@code uworldguard.bypass.auto} arms bypass on join without requiring the command permission.
 *
 * <p>Armed state is deliberately not persisted: {@link #clear} drops it on quit. Losing permission
 * while armed takes effect immediately, since permissions are re-checked on every call.
 *
 * <p>Static like {@link EventGate}, and for the same reason: it is consulted from three dozen places
 * across every listener, and threading a service through each of their constructors to carry one
 * boolean would cost far more than it explains. The set is concurrent — toggled from a command
 * thread, read from every region thread.
 */
@NullMarked
public final class Bypass {

    public static final String NODE = "uworldguard.bypass";
    public static final String AUTO_NODE = NODE + ".auto";

    /**
     * Holders who have armed their bypass. Absence means "off", so the default is that protection
     * applies to everyone.
     */
    private static final Set<UUID> armed = ConcurrentHashMap.newKeySet();

    private Bypass() {}

    /**
     * Whether {@code player} currently bypasses region protection — armed and still permitted.
     */
    public static boolean has(final Player player) {
        return !armed.isEmpty() && armed.contains(player.getUniqueId())
            && (player.hasPermission(NODE) || player.hasPermission(AUTO_NODE));
    }

    /**
     * Flips the player's bypass and returns its new state.
     */
    public static boolean toggle(final Player player) {
        final UUID uuid = player.getUniqueId();
        if (armed.remove(uuid)) {
            return false;
        }
        armed.add(uuid);
        return true;
    }

    public static void join(final Player player) {
        if (!player.hasPermission(AUTO_NODE)) {
            return;
        }

        armed.add(player.getUniqueId());
    }

    /**
     * Drop a player's state on quit, so bypass never survives a session and the set cannot grow
     * without bound.
     */
    public static void clear(final UUID uuid) {
        armed.remove(uuid);
    }
}