package com.layue13.clashweave.validation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.forge.CombatServer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Jar-independent render timing. A HIGHEST and a LOWEST handler on the same Forge render events bracket every other
 * handler, the mod's included, so the same fixture measures the pre-WF-1c jar and the content-driven jar alike. Times
 * are CPU wall time spent submitting the work (immediate-mode OBJ drawing is CPU-bound), not GPU time.
 */
public final class RenderProbe {

    private static final class Stat {

        long[] samples = new long[1 << 12];
        int count;

        void add(long nanos) {
            if (count == samples.length) samples = Arrays.copyOf(samples, count * 2);
            samples[count++] = nanos;
        }
    }

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<String, Stat> stats = new HashMap<>();
    private long specialsStart, handStart, waistStart, frameStart;
    private int frames;

    public RenderProbe() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private String category(EntityPlayer player) {
        ClientProxy.Visual visual = ClientProxy.instance == null ? null
            : ClientProxy.instance.visuals.get(player.getEntityId());
        return category(visual);
    }

    private String category(ClientProxy.Visual visual) {
        if (visual == null) return "no-state";
        String action = Ids.local(visual.state.action);
        if (action.isEmpty()) return visual.state.sheathed ? "sheathed-idle" : "drawn-idle";
        if (action.equals("iai")) return "draw";
        if (action.equals("sheathe")) return "sheathe";
        return "combo";
    }

    private void record(String phase, String category, long nanos) {
        stats.computeIfAbsent(phase + "/" + category, key -> new Stat())
            .add(nanos);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void specialsBegin(RenderPlayerEvent.Specials.Post event) {
        specialsStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void specialsEnd(RenderPlayerEvent.Specials.Post event) {
        long elapsed = System.nanoTime() - specialsStart;
        if (CombatServer.armed(event.entityPlayer)) record("specials", category(event.entityPlayer), elapsed);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void handBegin(RenderHandEvent event) {
        handStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void handEnd(RenderHandEvent event) {
        long elapsed = System.nanoTime() - handStart;
        if (mc.thePlayer != null && mc.gameSettings.thirdPersonView == 0 && CombatServer.armed(mc.thePlayer))
            record("hand", category(ClientProxy.instance.own()), elapsed);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void waistBegin(RenderWorldLastEvent event) {
        waistStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void waistEnd(RenderWorldLastEvent event) {
        long elapsed = System.nanoTime() - waistStart;
        if (mc.thePlayer != null && mc.gameSettings.thirdPersonView == 0 && CombatServer.armed(mc.thePlayer))
            record("waist", category(ClientProxy.instance.own()), elapsed);
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (mc.thePlayer == null || ClientProxy.instance == null) return;
        if (event.phase == TickEvent.Phase.START) {
            frameStart = System.nanoTime();
            return;
        }
        record("frame", category(ClientProxy.instance.own()), System.nanoTime() - frameStart);
        if (++frames % 600 == 0) report();
    }

    private static long percentile(long[] sorted, int count, double fraction) {
        return sorted[Math.min(count - 1, (int) Math.floor(fraction * count))];
    }

    private void report() {
        String who = mc.thePlayer.getCommandSenderName();
        for (Map.Entry<String, Stat> entry : new java.util.TreeMap<>(stats).entrySet()) {
            Stat stat = entry.getValue();
            long[] sorted = Arrays.copyOf(stat.samples, stat.count);
            Arrays.sort(sorted);
            long sum = 0;
            for (long value : sorted) sum += value;
            System.out.println(
                "WF1C_RENDER client=" + who
                    + " key="
                    + entry.getKey()
                    + " n="
                    + stat.count
                    + " meanUs="
                    + String.format(java.util.Locale.ROOT, "%.1f", sum / 1000.0 / stat.count)
                    + " p50Us="
                    + percentile(sorted, stat.count, .5) / 1000.0
                    + " p95Us="
                    + percentile(sorted, stat.count, .95) / 1000.0
                    + " p99Us="
                    + percentile(sorted, stat.count, .99) / 1000.0
                    + " maxUs="
                    + sorted[stat.count - 1] / 1000.0);
        }
    }
}
