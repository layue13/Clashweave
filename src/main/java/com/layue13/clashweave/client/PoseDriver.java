package com.layue13.clashweave.client;

import net.minecraft.entity.player.EntityPlayer;

import com.layue13.clashweave.client.pose.Pose;
import com.layue13.clashweave.client.pose.Track;
import com.layue13.clashweave.forge.CombatWeapons;

/**
 * Decides, once per rendered player and frame, whether the baked tracks pose the body and how far: the result is shared
 * with the weapon renderer, which draws the blade from the same pose. Everything is preallocated; the render path makes
 * no lookups by string.
 */
final class PoseDriver {

    static final PoseDriver INSTANCE = new PoseDriver();

    /** The pose of {@link #entity}; meaningful only while {@link #weight} is above zero. */
    final Pose pose = new Pose();
    int entity = Integer.MIN_VALUE;
    float weight;
    /** Whether the blade is in the hand, from the track while an action plays. */
    boolean bladeDrawn;
    /** The pose is the idle ready stance rather than an action. */
    boolean stance;

    private PoseDriver() {}

    /** Fills {@link #pose} and {@link #weight} for the player about to be drawn; leaves weight 0 when untouched. */
    void prepare(EntityPlayer player, ClientProxy.Visual visual) {
        entity = player.getEntityId();
        weight = 0;
        stance = false;
        if (visual == null) return;
        double ticks = 0;
        Track track = null;
        if (visual.state.action.isEmpty()) {
            if (!visual.state.sheathed) track = visual.stance;
        } else {
            track = visual.track;
            ticks = ClientProxy.instance.elapsed(visual, 0);
        }
        boolean wanted = track != null && !player.isPlayerSleeping() && CombatWeapons.armed(player.getHeldItem());
        // The body is taken over and handed back by chasing, at a rate that does not depend on the frame rate.
        float takeover = visual.rig.advance(wanted, System.nanoTime(), ClientProxy.instance.tickPeriod());
        if (takeover <= 0) return;
        if (track == null) {
            // Releasing: hold the last pose of whatever played so the fade has something to fade from.
            if (visual.lastTrack == null) return;
            visual.lastTrack.sample(visual.lastTicks, pose);
        } else {
            track.sample(ticks, pose);
            visual.lastTrack = track;
            visual.lastTicks = ticks;
        }
        pose.twistApplied = 0;
        bladeDrawn = visual.state.action.isEmpty() ? !visual.state.sheathed : pose.bladeDrawn;
        weight = takeover;
        stance = track != null && visual.state.action.isEmpty();
    }
}
