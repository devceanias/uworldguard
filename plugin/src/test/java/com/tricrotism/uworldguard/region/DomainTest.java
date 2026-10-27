package com.tricrotism.uworldguard.region;

import com.tricrotism.uworldguard.domain.DefaultDomain;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A domain's sorted player array answers as a set would, through adds and removes in any order,
 * including UUIDs whose halves are negative.
 */
class DomainTest {

    @Test
    void playersAnswerAsASetWouldThroughEdits() {
        final DefaultDomain domain = new DefaultDomain();
        final Set<UUID> expected = new HashSet<>();
        final Random random = new Random(9);
        final UUID[] pool = new UUID[64];
        for (int i = 0; i < pool.length; i++) {
            pool[i] = new UUID(random.nextLong(), random.nextLong());
        }
        for (int step = 0; step < 5000; step++) {
            final UUID uuid = pool[random.nextInt(pool.length)];
            if (random.nextBoolean()) {
                domain.addPlayer(uuid);
                expected.add(uuid);
            } else {
                domain.removePlayer(uuid);
                expected.remove(uuid);
            }
            for (final UUID probe : pool) {
                assertEquals(expected.contains(probe), domain.containsPlayer(probe));
            }
        }
        assertEquals(expected, domain.getPlayers());
        assertEquals(expected.size(), domain.size());
    }

    @Test
    void groupsAreKeptInLowerCase() {
        final DefaultDomain domain = new DefaultDomain();
        domain.addGroup("Staff");
        domain.addGroup("staff");

        assertTrue(domain.containsGroup("STAFF"));
        assertEquals(Set.of("staff"), domain.getGroups());
        assertTrue(domain.hasGroups());

        domain.removeGroup("sTaFf");

        assertFalse(domain.hasGroups());
        assertTrue(domain.isEmpty());
    }
}
