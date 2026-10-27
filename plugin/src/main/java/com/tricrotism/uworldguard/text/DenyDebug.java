package com.tricrotism.uworldguard.text;

import com.tricrotism.uworldguard.flags.Flag;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.flags.StateFlag;
import com.tricrotism.uworldguard.region.ApplicableRegionSet;
import com.tricrotism.uworldguard.region.ProtectedRegion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /uwg debug}: tells a player which flag refused them and which region set it.
 *
 * <p>Static like {@code Bypass}, since the refusals it explains happen in a dozen listeners. It is
 * only consulted after a refusal and an empty set answers first, so it costs nothing while nobody is
 * debugging. Cleared on quit.
 */
@NullMarked
public final class DenyDebug {

    public static final String NODE = "uworldguard.debug";

    private static final Set<UUID> watching = ConcurrentHashMap.newKeySet();
    /**
     * The last explanation each watcher got, so a held click or a flurry of hits explains once a
     * second rather than once a tick. Only watchers have entries, and they leave with {@link #clear}.
     */
    private static final Map<UUID, Sent> lastSent = new ConcurrentHashMap<>();
    private static final long REPEAT_MILLIS = 1000L;

    private record Sent(Flag<?> flag, long at) {}

    private DenyDebug() {}

    /**
     * Whether {@code player} wants explanations. For callers that would do extra work to build one.
     */
    public static boolean watching(final Player player) {
        return !watching.isEmpty() && watching.contains(player.getUniqueId());
    }

    /**
     * Flips the player's debug output and returns its new state.
     */
    public static boolean toggle(final Player player) {
        final UUID uuid = player.getUniqueId();
        if (watching.remove(uuid)) {
            lastSent.remove(uuid);
            return false;
        }
        watching.add(uuid);
        return true;
    }

    public static void clear(final UUID uuid) {
        watching.remove(uuid);
        lastSent.remove(uuid);
    }

    /**
     * Tells {@code player} why {@code flag} refused them, if they are debugging. {@code set} is the
     * regions where the refused thing happened, or {@code null} when the caller has none to hand.
     */
    public static void explain(final Player player, final Flag<?> flag, final @Nullable ApplicableRegionSet set) {
        send(player, flag, "refused you", set);
    }

    /**
     * Tells {@code player} which flag kept damage off them, if they are debugging.
     */
    public static void explainProtected(final Player player, final Flag<?> flag, final ApplicableRegionSet set) {
        send(player, flag, "kept damage off you", set);
    }

    private static void send(final Player player, final Flag<?> flag, final String outcome, final @Nullable ApplicableRegionSet set) {
        if (!watching(player)) {
            return;
        }
        final long now = System.currentTimeMillis();
        final Sent previous = lastSent.get(player.getUniqueId());
        if (previous != null && previous.flag() == flag && now - previous.at() < REPEAT_MILLIS) {
            return;
        }
        lastSent.put(player.getUniqueId(), new Sent(flag, now));
        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<dark_gray>[uwg debug]</dark_gray> <white><flag></white> <gray><outcome>: <reason>",
            Placeholder.unparsed("flag", flag.getName()),
            Placeholder.unparsed("outcome", outcome),
            Placeholder.unparsed("reason", reason(player.getUniqueId(), flag, set))));
    }

    private static String reason(final UUID uuid, final Flag<?> flag, final @Nullable ApplicableRegionSet set) {
        if (set == null) {
            return "no region detail for this check.";
        }
        if (!(flag instanceof StateFlag state)) {
            return "its value here is " + set.queryValue(flag, uuid) + ".";
        }
        final State explicit = set.queryExplicitState(state, uuid);
        if (explicit == State.DENY) {
            return "it is deny in " + setter(set, state, State.DENY) + ".";
        }
        if (explicit == State.ALLOW) {
            return "it is allow here, so another check decided (a deny list, or the flag it pairs with).";
        }
        if (set.queryExplicitState(Flags.BUILD, uuid) == State.DENY) {
            return "it is not set, and build is deny in " + setter(set, Flags.BUILD, State.DENY) + ".";
        }
        if (set.isEmpty()) {
            return "it is not set, and the global region decides here.";
        }
        return "it is not set, so membership decides, and you are not a member of "
            + set.get(0).getId() + " (priority " + set.get(0).getPriority() + ").";
    }

    /**
     * The highest-priority region whose value for {@code flag} is {@code value}, naming the parent it
     * inherits from when the region does not set it itself.
     */
    private static String setter(final ApplicableRegionSet set, final StateFlag flag, final State value) {
        for (int i = 0, n = set.size(); i < n; i++) {
            final ProtectedRegion region = set.get(i);
            if (region.getFlag(flag) != value) {
                continue;
            }
            if (region.getFlags().get(flag) == value) {
                return region.getId() + " (priority " + region.getPriority() + ")";
            }
            for (ProtectedRegion parent = region.getParent(); parent != null; parent = parent.getParent()) {
                if (parent.getFlags().get(flag) == value) {
                    return region.getId() + " (priority " + region.getPriority() + "), inherited from " + parent.getId();
                }
            }
        }
        return "the global region";
    }
}
