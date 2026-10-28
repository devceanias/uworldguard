package com.tricrotism.uworldguard.domain;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * A set of players (by UUID) and permission groups. Used for both owners and members.
 * Thread-safe: queried from region threads while edited from command threads.
 *
 * <p>Both are arrays replaced whole under this domain's monitor and read without it. Players are
 * kept sorted so a check is a binary search.
 */
@NullMarked
public final class DefaultDomain {

    /**
     * Prefix of the permission node a group's membership is read from, matching WorldGuard's own
     * convention and what LuckPerms grants every member of a group. Trusting {@code staff} means
     * trusting whoever holds {@code group.staff}.
     */
    private static final String GROUP_NODE_PREFIX = "group.";
    private static final UUID[] NO_PLAYERS = new UUID[0];
    private static final String[] NO_GROUPS = new String[0];

    private volatile UUID[] players = NO_PLAYERS;
    private volatile String[] groups = NO_GROUPS;

    /**
     * Where {@code uuid} is in {@code held}, or where it would go, as {@link Arrays#binarySearch}
     * answers and in the same order. The halves are compared directly because the library search
     * calls {@code compareTo} through an interface every step.
     */
    private static int search(final UUID[] held, final UUID uuid) {
        final long most = uuid.getMostSignificantBits();
        final long least = uuid.getLeastSignificantBits();
        int low = 0;
        int high = held.length - 1;
        while (low <= high) {
            final int mid = (low + high) >>> 1;
            final UUID at = held[mid];
            int order = Long.compare(at.getMostSignificantBits(), most);
            if (order == 0) {
                order = Long.compare(at.getLeastSignificantBits(), least);
            }
            if (order < 0) {
                low = mid + 1;
            } else if (order > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    public synchronized void addPlayer(final UUID uuid) {
        final UUID[] held = players;
        final int at = search(held, uuid);
        if (at >= 0) {
            return;
        }
        final int insert = -at - 1;
        final UUID[] out = new UUID[held.length + 1];
        System.arraycopy(held, 0, out, 0, insert);
        out[insert] = uuid;
        System.arraycopy(held, insert, out, insert + 1, held.length - insert);
        players = out;
    }

    public synchronized void removePlayer(final UUID uuid) {
        final UUID[] held = players;
        final int at = search(held, uuid);
        if (at < 0) {
            return;
        }
        if (held.length == 1) {
            players = NO_PLAYERS;
            return;
        }
        final UUID[] out = new UUID[held.length - 1];
        System.arraycopy(held, 0, out, 0, at);
        System.arraycopy(held, at + 1, out, at, held.length - at - 1);
        players = out;
    }

    public synchronized void addGroup(final String group) {
        final String key = group.toLowerCase(Locale.ROOT);
        final String[] held = groups;
        if (indexOf(held, key) < 0) {
            final String[] out = Arrays.copyOf(held, held.length + 1);
            out[held.length] = key;
            groups = out;
        }
    }

    public synchronized void removeGroup(final String group) {
        final String[] held = groups;
        final int at = indexOf(held, group.toLowerCase(Locale.ROOT));
        if (at < 0) {
            return;
        }
        if (held.length == 1) {
            groups = NO_GROUPS;
            return;
        }
        final String[] out = new String[held.length - 1];
        System.arraycopy(held, 0, out, 0, at);
        System.arraycopy(held, at + 1, out, at, held.length - at - 1);
        groups = out;
    }

    private static int indexOf(final String[] held, final String key) {
        for (int i = 0; i < held.length; i++) {
            if (held[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    public boolean containsPlayer(final UUID uuid) {
        final UUID[] held = players;
        return held.length != 0 && search(held, uuid) >= 0;
    }

    public boolean containsGroup(final String group) {
        return indexOf(groups, group.toLowerCase(Locale.ROOT)) >= 0;
    }

    /**
     * Whether this domain trusts any group at all. This is the guard that keeps group support off the
     * membership hot path: a region trusting nobody by group never reaches {@link #grants}.
     */
    public boolean hasGroups() {
        return groups.length != 0;
    }

    /**
     * Whether {@code player} holds the permission node of a group this domain trusts.
     *
     * <p>Group membership lives in the permission plugin, so it is read as a node per trusted group.
     * An offline player has no permissions to ask, which is why {@link #containsPlayer} stays the
     * check for anything that must work without the player present.
     */
    public boolean grants(final Player player) {
        for (final String group : groups) {
            if (player.hasPermission(GROUP_NODE_PREFIX + group)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The players as they are now, in UUID order. A snapshot: later edits do not show in it.
     */
    public Set<UUID> getPlayers() {
        return new Snapshot<>(players, true);
    }

    /**
     * The groups as they are now, in lower case. A snapshot: later edits do not show in it.
     */
    public Set<String> getGroups() {
        return new Snapshot<>(groups, false);
    }

    public boolean isEmpty() {
        return players.length == 0 && groups.length == 0;
    }

    public int size() {
        return players.length + groups.length;
    }

    /**
     * A read-only set over one of the arrays. Its elements are distinct by construction.
     */
    private static final class Snapshot<E> extends AbstractSet<E> {
        private final E[] elements;
        private final boolean sorted;

        Snapshot(final E[] elements, final boolean sorted) {
            this.elements = elements;
            this.sorted = sorted;
        }

        @Override
        public Iterator<E> iterator() {
            return Arrays.asList(elements).iterator();
        }

        @Override
        public int size() {
            return elements.length;
        }

        @Override
        public boolean contains(final @Nullable Object o) {
            if (sorted && o instanceof UUID uuid) {
                return search((UUID[]) elements, uuid) >= 0;
            }
            for (final E element : elements) {
                if (element.equals(o)) {
                    return true;
                }
            }
            return false;
        }
    }
}
