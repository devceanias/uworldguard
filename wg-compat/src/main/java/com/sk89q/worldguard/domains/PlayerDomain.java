// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.domains;

import com.sk89q.worldguard.util.ChangeTracked;

import java.util.Set;
import java.util.UUID;

/**
 * The player half of a {@link DefaultDomain}: a live view of the UUID set inside a uWorldGuard
 * {@code com.tricrotism.uworldguard.domain.DefaultDomain}.
 *
 * <p>When this view belongs to a region, every edit that changes it marks that region's world dirty,
 * because engine domain edits do not persist by themselves.
 */
public class PlayerDomain implements Domain, ChangeTracked {

    private final com.tricrotism.uworldguard.domain.DefaultDomain backing;
    private final Runnable onChange;
    private boolean dirty = true;

    public PlayerDomain() {
        this(new com.tricrotism.uworldguard.domain.DefaultDomain(), DefaultDomain.NO_CHANGE);
    }

    public PlayerDomain(final PlayerDomain domain) {
        this();
        for (final UUID uniqueId : domain.getUniqueIds()) {
            backing.addPlayer(uniqueId);
        }
    }

    /**
     * @deprecated uWorldGuard stores members by UUID. Names that are not in the server's player cache
     * are dropped.
     */
    @Deprecated
    public PlayerDomain(final String[] names) {
        this();
        for (final String name : names) {
            addPlayer(name);
        }
    }

    PlayerDomain(final com.tricrotism.uworldguard.domain.DefaultDomain backing, final Runnable onChange) {
        this.backing = backing;
        this.onChange = onChange;
    }

    public Set<UUID> getUniqueIds() {
        return backing.getPlayers();
    }

    /**
     * @deprecated resolves names from the server's player cache; unresolvable UUIDs are omitted.
     */
    @Deprecated
    public Set<String> getPlayers() {
        return DefaultDomain.resolveNames(backing.getPlayers());
    }

    public void addPlayer(final UUID uniqueId) {
        if (backing.containsPlayer(uniqueId)) {
            return;
        }
        backing.addPlayer(uniqueId);
        changed();
    }

    /**
     * @deprecated see {@link #getPlayers()}.
     */
    @Deprecated
    public void addPlayer(final String name) {
        final UUID uniqueId = com.tricrotism.uworldguard.wgcompat.NameResolver.uuid(name);
        if (uniqueId == null) {
            com.tricrotism.uworldguard.wgcompat.NameResolver.warnUnresolved("PlayerDomain.addPlayer(String)", name);
            com.tricrotism.uworldguard.wgcompat.CompatDiagnostics.stub("PlayerDomain.addPlayer(String)");
            return;
        }
        addPlayer(uniqueId);
    }

    public void removePlayer(final UUID uuid) {
        if (!backing.containsPlayer(uuid)) {
            return;
        }
        backing.removePlayer(uuid);
        changed();
    }

    /**
     * @deprecated see {@link #getPlayers()}.
     */
    @Deprecated
    public void removePlayer(final String name) {
        final UUID uniqueId = com.tricrotism.uworldguard.wgcompat.NameResolver.uuid(name);
        if (uniqueId != null) {
            removePlayer(uniqueId);
        }
    }

    @Override
    public boolean contains(final UUID uniqueId) {
        return backing.containsPlayer(uniqueId);
    }

    @Override
    public boolean contains(final com.sk89q.worldguard.LocalPlayer player) {
        return backing.containsPlayer(player.getUniqueId());
    }

    public void addPlayer(final com.sk89q.worldguard.LocalPlayer player) {
        addPlayer(player.getUniqueId());
    }

    public void removePlayer(final com.sk89q.worldguard.LocalPlayer player) {
        removePlayer(player.getUniqueId());
    }

    @Override
    @Deprecated
    public boolean contains(final String playerName) {
        final UUID uniqueId = com.tricrotism.uworldguard.wgcompat.NameResolver.uuid(playerName);
        return uniqueId != null && backing.containsPlayer(uniqueId);
    }

    @Override
    public int size() {
        return backing.getPlayers().size();
    }

    @Override
    public void clear() {
        final Set<UUID> players = backing.getPlayers();
        if (players.isEmpty()) {
            return;
        }
        for (final UUID uniqueId : players) {
            backing.removePlayer(uniqueId);
        }
        changed();
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void setDirty(final boolean dirty) {
        this.dirty = dirty;
    }

    com.tricrotism.uworldguard.domain.DefaultDomain uwgBacking() {
        return backing;
    }

    private void changed() {
        dirty = true;
        onChange.run();
    }

    @Override
    public String toString() {
        return "PlayerDomain{" + backing.getPlayers() + '}';
    }
}
