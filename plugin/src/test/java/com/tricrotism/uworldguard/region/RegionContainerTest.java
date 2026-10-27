package com.tricrotism.uworldguard.region;

import com.tricrotism.uworldguard.storage.RegionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which manager a world resolves to, found by reference and gone with the world.
 */
class RegionContainerTest {

    private static final RegionStore NO_STORE = new RegionStore() {
        @Override
        public void load(final String worldName, final RegionManager manager) {}

        @Override
        public void save(final String worldName, final RegionManager manager) {}
    };

    private ServerMock server;
    private RegionContainerImpl container;
    private WorldMock overworld;
    private WorldMock nether;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        overworld = server.addSimpleWorld("world");
        nether = server.addSimpleWorld("world_nether");
        container = new RegionContainerImpl(MockBukkit.createMockPlugin("uWorldGuard"), NO_STORE);
        container.loadAll();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void eachWorldHasItsOwnManager() {
        final RegionManager first = container.get(overworld);
        final RegionManager second = container.get(nether);

        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
        assertSame(first, container.get(overworld));
    }

    @Test
    void anUnloadedWorldHasNoManager() {
        container.unload(nether);

        assertNull(container.get(nether));
        assertNotNull(container.get(overworld));
    }
}
