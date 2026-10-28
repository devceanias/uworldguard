package com.tricrotism.uworldguard.text;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A player standing in a chat-prefix region hands the same tag back on every crossing, which must
 * not rewrite the entry the chat thread reads.
 */
class ChatTagsTest {

    @Test
    void anUnchangedTagIsNotWrittenAgain() {
        final ChatTags tags = new ChatTags();
        final UUID uuid = UUID.randomUUID();
        // Distinct but equal instances, so a rewrite shows up as a different object
        final String prefix = new String("[VIP]");
        final String suffix = new String("!");
        tags.setPrefix(uuid, prefix);
        tags.setSuffix(uuid, suffix);

        tags.setPrefix(uuid, new String("[VIP]"));
        tags.setSuffix(uuid, new String("!"));

        assertSame(prefix, tags.prefix(uuid));
        assertSame(suffix, tags.suffix(uuid));
    }

    @Test
    void aChangedTagIsStillWritten() {
        final ChatTags tags = new ChatTags();
        final UUID uuid = UUID.randomUUID();
        tags.setPrefix(uuid, "[VIP]");

        tags.setPrefix(uuid, "[MVP]");

        assertEquals("[MVP]", tags.prefix(uuid));
    }
}
