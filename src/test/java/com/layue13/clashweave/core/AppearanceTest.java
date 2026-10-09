package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;

import com.layue13.clashweave.network.StateMessage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class AppearanceTest {

    @Test
    public void onlyIdsRoundTripAndReachObservers() {
        StateMessage message = new StateMessage();
        message.appearance = new AppearanceState("example:skin", "example:effects", "example:animation");
        message.session = 123;
        ByteBuf data = Unpooled.buffer();
        try {
            new StateMessage(message).toBytes(data);
            StateMessage decoded = new StateMessage();
            decoded.fromBytes(data);
            assertEquals(message.appearance, decoded.appearance);
            assertEquals(0, decoded.session);
            assertEquals("katana", decoded.style);
        } finally {
            data.release();
        }
        assertThrows(IllegalArgumentException.class, () -> new AppearanceState("../BAD", "ok", "ok"));
        assertThrows(
            IllegalArgumentException.class,
            () -> new AppearanceState(new String(new char[65]).replace('\0', 'a'), "ok", "ok"));
    }

    @Test
    public void pendingAppearanceWaitsForSafePointWithoutRestartOrHitReset() {
        AppearanceState.Pending pending = new AppearanceState.Pending();
        AppearanceState next = new AppearanceState("example:skin", "example:effects", "example:animation");
        ActionCatalog catalog = new ActionCatalog(
            new java.io.InputStreamReader(
                getClass().getResourceAsStream("/assets/clashweave/combat/katana.json"),
                java.nio.charset.StandardCharsets.UTF_8));
        Scheduler scheduler = new Scheduler(catalog);
        scheduler.request(Intent.LIGHT, 1, 0);
        scheduler.tick(0);
        Scheduler.Instance action = scheduler.current();
        assertTrue(action.claim("target", 0));
        pending.request(next);
        assertFalse(pending.apply(scheduler.current() == null));
        assertEquals(AppearanceState.DEFAULT, pending.current());
        assertSame(action, scheduler.current());
        assertFalse(action.claim("target", 0));
        assertThrows(IllegalStateException.class, () -> scheduler.useWeapon(WeaponDefinition.katana(), catalog));
        scheduler.tick(action.definition.duration());
        assertTrue(pending.apply(scheduler.current() == null));
        assertEquals(next, pending.current());
        assertNull(scheduler.current());
    }
}
