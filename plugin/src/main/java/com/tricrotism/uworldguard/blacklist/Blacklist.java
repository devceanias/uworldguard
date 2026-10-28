package com.tricrotism.uworldguard.blacklist;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.logging.Logger;

/**
 * One world's blacklist, compiled from {@code config.yml} into a table indexed by material so an
 * unlisted item costs one array load. Immutable once built. A reload swaps in a new one whole.
 *
 * <p>Follows WorldGuard's semantics. Every entry naming the material is checked and any one refusing
 * refuses the action. An entry starts from allow, or from deny in whitelist mode, and applies its
 * actions for the rule in order: {@code deny} and {@code allow} set the result, the rest only report.
 * So in whitelist mode a listed item is refused for every rule that does not say {@code allow}, and
 * an unlisted item is left alone.
 */
@NullMarked
public final class Blacklist {

    /**
     * What a player did, named as WorldGuard names it in config and in messages.
     */
    public enum Rule {
        BREAK("on-break", "break"),
        PLACE("on-place", "place"),
        INTERACT("on-interact", "interact with"),
        DISPENSE("on-dispense", "dispense"),
        DESTROY_WITH("on-destroy-with", "destroy with"),
        ACQUIRE("on-acquire", "acquire"),
        EQUIP("on-equip", "equip"),
        DROP("on-drop", "drop"),
        USE("on-use", "use");

        private static final Rule[] VALUES = values();

        private final String key;
        private final String description;

        Rule(final String key, final String description) {
            this.key = key;
            this.description = description;
        }

        public String key() {
            return key;
        }

        public String description() {
            return description;
        }

        public static @Nullable Rule byKey(final String key) {
            for (final Rule rule : VALUES) {
                if (rule.key.equalsIgnoreCase(key)) {
                    return rule;
                }
            }
            return null;
        }
    }

    public enum Action {
        ALLOW, DENY, NOTIFY, LOG, TELL, KICK, BAN;

        public static @Nullable Action byName(final String name) {
            for (final Action action : values()) {
                if (action.name().equalsIgnoreCase(name)) {
                    return action;
                }
            }
            return null;
        }
    }

    private static final Action[] NO_ACTIONS = new Action[0];

    /**
     * One configured entry. {@code actions} is indexed by {@link Rule#ordinal()}, empty where the
     * entry says nothing about that rule.
     */
    public record Entry(
        Set<Material> items, List<String> ignorePerms, List<String> ignoreGroups,
        @Nullable String message, @Nullable String comment, Action[][] actions
    ) {
        public Action[] actions(final Rule rule) {
            return actions[rule.ordinal()];
        }

        /**
         * Whether {@code ignore-perms} or {@code ignore-groups} lets the player past. A group is the
         * permission {@code group.<name>}, which LuckPerms grants to its members.
         */
        boolean ignores(final Player player) {
            for (final String perm : ignorePerms) {
                if (player.hasPermission(perm)) {
                    return true;
                }
            }
            for (final String group : ignoreGroups) {
                if (player.hasPermission(group)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final boolean whitelist;
    private final List<Entry> entries;
    private final Entry[] @Nullable [] byMaterial;
    private final boolean[] usedRules = new boolean[Rule.VALUES.length];

    private Blacklist(final boolean whitelist, final List<Entry> entries) {
        this.whitelist = whitelist;
        this.entries = List.copyOf(entries);
        final Entry[][] table = new Entry[Material.values().length][];
        for (final Entry entry : entries) {
            for (final Rule rule : Rule.VALUES) {
                usedRules[rule.ordinal()] |= whitelist || entry.actions(rule).length > 0;
            }
            for (final Material item : entry.items()) {
                final Entry[] current = table[item.ordinal()];
                final Entry[] grown = current == null ? new Entry[1] : Arrays.copyOf(current, current.length + 1);
                grown[grown.length - 1] = entry;
                table[item.ordinal()] = grown;
            }
        }
        this.byMaterial = table;
    }

    /**
     * @return the list, or {@code null} when there is nothing in it, so a world without one costs a
     * null check
     */
    static @Nullable Blacklist of(final boolean whitelist, final List<Entry> entries) {
        return entries.isEmpty() ? null : new Blacklist(whitelist, entries);
    }

    public boolean whitelist() {
        return whitelist;
    }

    public List<Entry> entries() {
        return entries;
    }

    /**
     * Whether any entry can act on {@code rule}. Every rule can in whitelist mode, where a listed item
     * with nothing said is refused. Lets a handler skip reading what it would check.
     */
    public boolean uses(final Rule rule) {
        return usedRules[rule.ordinal()];
    }

    /**
     * The entries naming {@code material}, or {@code null} when none does.
     */
    public Entry @Nullable [] entriesFor(final Material material) {
        return byMaterial[material.ordinal()];
    }

    /**
     * Reads entries as {@code config.yml} holds them. A bad name or action is reported and skipped,
     * so one typo does not take the rest of the list with it.
     */
    static List<Entry> parse(final List<Map<?, ?>> raw, final String path, final Logger log) {
        final List<Entry> entries = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            final Map<?, ?> map = raw.get(i);
            final String where = path + " #" + (i + 1);
            final Set<Material> items = EnumSet.noneOf(Material.class);
            for (final String name : strings(map.get("items"))) {
                final Material material = Material.matchMaterial(name);
                if (material == null) {
                    log.warning("Unknown material in " + where + ", ignoring it: " + name);
                } else {
                    items.add(material);
                }
            }
            if (items.isEmpty()) {
                log.warning(where + " names no known item, so it is ignored.");
                continue;
            }
            final Action[][] actions = new Action[Rule.VALUES.length][];
            Arrays.fill(actions, NO_ACTIONS);
            for (final Map.Entry<?, ?> option : map.entrySet()) {
                final String key = String.valueOf(option.getKey());
                final Rule rule = Rule.byKey(key);
                if (rule != null) {
                    actions[rule.ordinal()] = actions(option.getValue(), where, log);
                } else if (!KNOWN_OPTIONS.contains(key)) {
                    log.warning("Unknown option in " + where + ", ignoring it: " + key);
                }
            }
            final List<String> groups = new ArrayList<>();
            for (final String group : strings(map.get("ignore-groups"))) {
                groups.add("group." + group.toLowerCase(Locale.ROOT));
            }
            entries.add(new Entry(Collections.unmodifiableSet(items), strings(map.get("ignore-perms")),
                List.copyOf(groups), text(map.get("message")), text(map.get("comment")), actions));
        }
        return entries;
    }

    private static final Set<String> KNOWN_OPTIONS =
        Set.of("items", "ignore-perms", "ignore-groups", "message", "comment");

    private static Action[] actions(final @Nullable Object value, final String where, final Logger log) {
        final List<Action> actions = new ArrayList<>();
        for (final String name : strings(value)) {
            final Action action = Action.byName(name);
            if (action == null) {
                log.warning("Unknown action in " + where + ", ignoring it: " + name);
            } else {
                actions.add(action);
            }
        }
        return actions.isEmpty() ? NO_ACTIONS : actions.toArray(NO_ACTIONS);
    }

    /**
     * A YAML list, or one comma-separated string, since both read naturally in config.
     */
    public static List<String> strings(final @Nullable Object value) {
        if (value == null) {
            return List.of();
        }
        final List<String> out = new ArrayList<>();
        final Collection<?> parts = value instanceof Collection<?> list ? list : List.of(String.valueOf(value).split(","));
        for (final Object part : parts) {
            final String trimmed = String.valueOf(part).trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return List.copyOf(out);
    }

    private static @Nullable String text(final @Nullable Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }
}
