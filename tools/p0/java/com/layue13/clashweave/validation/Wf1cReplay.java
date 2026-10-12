package com.layue13.clashweave.validation;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.ScreenShotHelper;

import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * WF-1c client driver. P0A presses attack through the production intent path whenever a real player could (no GUI, not
 * dead); both clients respawn after a death screen and reconnect after losing the connection. It decides nothing
 * about outcomes: the server fixture applies the lifecycle events and checks them.
 */
public final class Wf1cReplay {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final String mode = System.getProperty("cw.wf1c", "");
    private int ticks, connected, dead, lost, sinceJoin;
    private boolean wasIn, waistLogged;
    private final Set<String> seen = new HashSet<>();
    private final Set<String> captured = new HashSet<>();
    private String shot;
    private boolean orbit;
    private int shots;

    public Wf1cReplay() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    private boolean attacker() {
        return mc.getSession()
            .getUsername()
            .equals("P0A") && !mode.startsWith("float");
    }

    private void photo(String name, int view, float angle) {
        if (captured.contains(name) || shot != null) return;
        mc.gameSettings.thirdPersonView = view;
        mc.thePlayer.rotationPitch = 0;
        if (view != 0) {
            EntityOtherPlayerMP camera = new EntityOtherPlayerMP(
                mc.theWorld,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Photo"));
            camera.setPosition(mc.thePlayer.posX, mc.thePlayer.boundingBox.minY, mc.thePlayer.posZ);
            camera.prevPosX = camera.lastTickPosX = camera.posX;
            camera.prevPosY = camera.lastTickPosY = camera.posY;
            camera.prevPosZ = camera.lastTickPosZ = camera.posZ;
            camera.rotationYaw = camera.prevRotationYaw = angle;
            mc.renderViewEntity = camera;
            orbit = true;
        }
        shot = name;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        if (mc.thePlayer == null || mc.theWorld == null) {
            if (wasIn) {
                wasIn = false;
                lost = 0;
                connected = 0;
                System.out.println("WF1C_CLIENT_LOST name=" + mc.getSession().getUsername() + " tick=" + ticks);
            }
            if (!wasIn && ++lost == 100 && ticks > 200) {
                System.out.println("WF1C_CLIENT_RECONNECT name=" + mc.getSession().getUsername());
                cpw.mods.fml.client.FMLClientHandler.instance()
                    .connectToServer(null, new ServerData("P0", "127.0.0.1:" + Integer.getInteger("cw.p0.port", 25580)));
            }
            return;
        }
        wasIn = true;
        if (mc.thePlayer.getHealth() <= 0 || mc.thePlayer.isDead) {
            // A player who sees the death screen would click Respawn after a moment.
            if (++dead == 40) {
                dead = 0;
                System.out.println("WF1C_CLIENT_RESPAWN name=" + mc.thePlayer.getCommandSenderName() + " tick=" + ticks);
                mc.thePlayer.respawnPlayer();
            }
            if (attacker() && dead % 8 == 1) {
                // Lifecycle probe, not gameplay: the server must refuse input from a dead player.
                ClientProxy.instance.send(Intent.LIGHT, -1);
            }
            return;
        }
        dead = 0;
        ClientProxy proxy = ClientProxy.instance;
        ClientProxy.Visual own = proxy.own();
        if (own == null) return;
        connected++;
        sinceJoin++;
        for (ClientProxy.Visual visual : proxy.visuals.values()) {
            String key = visual.state.entity + "|" + visual.state.style + "|" + visual.state.appearance.skin + "|"
                + visual.state.appearance.animations
                + "|"
                + visual.resolvedRender()
                + "|"
                + visual.hasPreset();
            if (seen.add(key)) System.out.println(
                "WF1C_SEEN observer=" + mc.thePlayer.getCommandSenderName()
                    + " entity="
                    + visual.state.entity
                    + " style="
                    + visual.state.style
                    + " skin="
                    + visual.state.appearance.skin
                    + " animation="
                    + visual.state.appearance.animations
                    + " resolvedRender="
                    + visual.resolvedRender()
                    + " presetResolved="
                    + visual.hasPreset()
                    + " catalogNamespace="
                    + (visual.catalog == null ? "none" : visual.catalog.namespace));
        }
        if (connected % 20 == 0 && attacker()) System.out.println(
            "WF1C_CLIENT name=" + mc.thePlayer.getCommandSenderName()
                + " tick="
                + ticks
                + " instance="
                + own.state.instance
                + " action="
                + own.state.action
                + " style="
                + own.state.style
                + " sheathed="
                + own.state.sheathed
                + " predicted="
                + proxy.predicted(own)
                + " gui="
                + (mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName())
                + " riding="
                + (mc.thePlayer.ridingEntity != null)
                + " dim="
                + mc.thePlayer.dimension
                + " corrections="
                + proxy.corrections);
        boolean photoWindow = mode.equals("weapon2") && connected >= 200 && connected < 420;
        if (photoWindow) {
            photograph(proxy, own);
            return;
        }
        if (attacker() && connected > 60 && mc.currentScreen == null && connected % 4 == 0)
            proxy.send(((connected / 4) % 7 == 6) ? Intent.HEAVY : Intent.LIGHT, -1);
    }

    private void photograph(ClientProxy proxy, ClientProxy.Visual own) {
        mc.gameSettings.hideGUI = true;
        mc.gameSettings.limitFramerate = 60;
        String name = mc.thePlayer.getCommandSenderName();
        if (name.equals("P0A")) {
            mc.thePlayer.rotationYaw = 0;
            if (!own.state.action.isEmpty() || shot != null) return;
            if (!captured.contains("glaive-first") && connected >= 215) photo("glaive-first", 0, 0);
            else if (!captured.contains("glaive-front") && captured.contains("glaive-first")) photo("glaive-front", 2, 0);
            else if (!captured.contains("glaive-side") && captured.contains("glaive-front")) photo("glaive-side", 1, 90);
        } else if (connected >= 245 && !captured.contains("observer-glaive") && shot == null) {
            for (Object object : mc.theWorld.playerEntities) {
                net.minecraft.entity.player.EntityPlayer remote = (net.minecraft.entity.player.EntityPlayer) object;
                if (!remote.getCommandSenderName().equals("P0A")) continue;
                mc.gameSettings.thirdPersonView = 1;
                mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = 20
                    + (float) Math.toDegrees(Math.atan2(-(remote.posX - mc.thePlayer.posX), remote.posZ - mc.thePlayer.posZ));
                mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 8;
                shot = "observer-glaive";
            }
        }
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || mc.thePlayer == null || shot == null) return;
        ScreenShotHelper
            .saveScreenshot(new File("."), "wf1c-" + shot + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        ClientProxy.Visual own = ClientProxy.instance.own();
        System.out.println(
            "WF1C_CAPTURE name=" + shot
                + " tick="
                + ticks
                + " style="
                + (own == null ? "" : own.state.style)
                + " action="
                + (own == null ? "" : own.state.action)
                + " sheathed="
                + (own != null && own.state.sheathed));
        captured.add(shot);
        shot = null;
        shots++;
        if (orbit) {
            mc.renderViewEntity = mc.thePlayer;
            orbit = false;
        }
    }
}
