// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.domains;

import com.sk89q.worldguard.util.ChangeTracked;

import java.util.Set;
import java.util.UUID;

/**
 * The permission-group half of a {@link DefaultDomain}: a live view of the group-name set inside a
 * uWorldGuard {@code com.tricrotism.uworldguard.domain.DefaultDomain}.
 *
 * <p>The engine lowercases group names on insertion, so {@link #getGroups()} always answers in lower
 * case regardless of what was added.
 */
public class GroupDomain implements Domain, ChangeTracked {

    private final com.tricrotism.uworldguard.domain.DefaultDomain backing;
    private final Runnable onChange;
    private boolean dirty = true;

    public GroupDomain() {
        this(new com.tricrotism.uworldguard.domain.DefaultDomain(), DefaultDomain.NO_CHANGE);
    }

    public GroupDomain(final GroupDomain domain) {
        this();
        for (final String group : domain.getGroups()) {
            backing.addGroup(group);
        }
    }

    public GroupDomain(final String[] groups) {
        this();
        for (final String group : groups) {
            backing.addGroup(group);
        }
    }

    GroupDomain(final com.tricrotism.uworldguard.domain.DefaultDomain backing, final Runnable onChange) {
        this.backing = backing;
        this.onChange = onChange;
    }

    public Set<String> getGroups() {
        return backing.getGroups();
    }

    public void addGroup(final String name) {
        if (backing.containsGroup(name)) {
            return;
        }
        backing.addGroup(name);
        changed();
    }

    public void removeGroup(final String name) {
        if (!backing.containsGroup(name)) {
            return;
        }
        backing.removeGroup(name);
        changed();
    }

    /**
     * Always {@code false}: a player name is never a permission group.
     */
    @Override
    @Deprecated
    public boolean contains(final String playerName) {
        return false;
    }

    /**
     * Answered from the player's {@code group.<name>} permission nodes, so it only holds for a player
     * who is online — an offline UUID has no permissible to ask.
     */
    @Override
    public boolean contains(final UUID uniqueId) {
        for (final String group : backing.getGroups()) {
            if (com.tricrotism.uworldguard.wgcompat.Groups.inGroup(uniqueId, group)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Asks the player itself, which is the only way group membership can be answered.
     */
    @Override
    public boolean contains(final com.sk89q.worldguard.LocalPlayer player) {
        for (final String group : backing.getGroups()) {
            if (player.hasGroup(group)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int size() {
        return backing.getGroups().size();
    }

    @Override
    public void clear() {
        final Set<String> groups = backing.getGroups();
        if (groups.isEmpty()) {
            return;
        }
        for (final String group : groups) {
            backing.removeGroup(group);
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
        return "GroupDomain{" + backing.getGroups() + '}';
    }
}
