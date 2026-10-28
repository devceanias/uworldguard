package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.config.Bypass;
import com.tricrotism.uworldguard.config.EventGate;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.region.ApplicableRegionSet;
import com.tricrotism.uworldguard.region.RegionQuery;
import com.tricrotism.uworldguard.text.MessageService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Enforces {@code vehicle-place} and {@code vehicle-destroy}. Boats and minecarts are entities, so
 * neither placing nor breaking one goes through the block-place or block-break flags — without this
 * they stay free to leave and to smash inside an otherwise protected region.
 */
@NullMarked
public final class VehicleListener implements Listener {

    private final RegionQuery query;
    private final MessageService messages;

    public VehicleListener(final RegionQuery query, final MessageService messages) {
        this.query = query;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(final EntityPlaceEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!(event.getEntity() instanceof Vehicle vehicle)) {
            return;
        }
        final Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (!query.getApplicableRegions(vehicle).testBuild(player.getUniqueId(), Flags.VEHICLE_PLACE)) {
            if (Bypass.has(player)) {
                return;
            }
            event.setCancelled(true);
            messages.sendDeny(player, Flags.VEHICLE_PLACE);
        }
    }

    /**
     * Covers destruction by anything, not just players. A player attacker is still resolved so bypass
     * and the denial message work. Anything else is judged by where it came from, so a blast from
     * outside a claim cannot break the carts inside it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDestroy(final VehicleDestroyEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Player attacker = Attackers.of(event.getAttacker());
        if (mayDestroy(event.getVehicle(), event.getAttacker(), attacker)) {
            return;
        }
        event.setCancelled(true);
        if (attacker != null) {
            messages.sendDeny(attacker, Flags.VEHICLE_DESTROY);
        }
    }

    /**
     * Damage short of destruction. A minecart survives several hits, and each one is a
     * {@code VehicleDamageEvent} rather than a {@code VehicleDestroyEvent} — so gating only the final
     * blow would let an outsider whittle a vehicle down and merely fail on the last hit.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(final VehicleDamageEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!mayDestroy(event.getVehicle(), event.getAttacker(), Attackers.of(event.getAttacker()))) {
            event.setCancelled(true);
        }
    }

    private boolean mayDestroy(final Vehicle vehicle, final @Nullable Entity cause, final @Nullable Player attacker) {
        final ApplicableRegionSet at = query.getApplicableRegions(vehicle);
        if (attacker == null) {
            return Attackers.sourceMayBuild(query, cause, at, Flags.VEHICLE_DESTROY);
        }
        return at.testBuild(attacker.getUniqueId(), Flags.VEHICLE_DESTROY) || Bypass.has(attacker);
    }
}
