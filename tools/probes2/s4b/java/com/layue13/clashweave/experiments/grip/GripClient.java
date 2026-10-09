package com.layue13.clashweave.experiments.grip;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.common.MinecraftForge;

public class GripClient extends GripProxy {
    private boolean connected;
    private int worldTicks;
    private int shot=-1;
    @Override public void init() {
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(new GripRendering());
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.START) return;
        Minecraft mc=Minecraft.getMinecraft();
        if(!connected && mc.currentScreen instanceof GuiMainMenu) {
            connected=true;
            mc.gameSettings.pauseOnLostFocus=false;
            mc.gameSettings.renderDistanceChunks=4;
            mc.gameSettings.limitFramerate=60;
            mc.gameSettings.viewBobbing=false;
            cpw.mods.fml.client.FMLClientHandler.instance().setupServerList();
            cpw.mods.fml.client.FMLClientHandler.instance().connectToServer(mc.currentScreen,
                new net.minecraft.client.multiplayer.ServerData("S4b grip","127.0.0.1:25574"));
        }
        if(mc.thePlayer==null || mc.theWorld==null) return;
        KeyBinding.unPressAllKeys();
        mc.setIngameNotInFocus();
        mc.thePlayer.inventory.mainInventory[0]=new ItemStack(GripExperiment.BLADE);
        mc.thePlayer.inventory.currentItem=0;
        mc.thePlayer.rotationPitch=0;
        mc.thePlayer.rotationYaw=worldTicks>=200 ? -45 : 25;
        mc.thePlayer.renderYawOffset=mc.thePlayer.rotationYaw;
        mc.thePlayer.rotationYawHead=mc.thePlayer.rotationYaw;
        mc.theWorld.setWorldTime(6000);
        worldTicks++;
        if(worldTicks==80 || worldTicks==120 || worldTicks==160 || worldTicks==200) {
            int i=(worldTicks-80)/40;
            mc.gameSettings.thirdPersonView=i<2 ? 0 : 2;
            GripRendering.pose=i%2;
            mc.thePlayer.rotationYaw=i==3 ? -45 : 25;
            mc.thePlayer.rotationYawHead=mc.thePlayer.rotationYaw;
            mc.thePlayer.renderYawOffset=mc.thePlayer.rotationYaw;
        }
        if(worldTicks==90 || worldTicks==130 || worldTicks==170 || worldTicks==210) {
            shot=(worldTicks-90)/40;
            GripExperiment.log("CAPTURE index="+shot+" view="+mc.gameSettings.thirdPersonView+" pose="+GripRendering.pose+" tick="+worldTicks);
        }
        if(worldTicks>230) { GripExperiment.log("COMPLETE screenshots=4"); mc.shutdown(); }
    }
    @SubscribeEvent public void render(TickEvent.RenderTickEvent event) {
        if(event.phase==TickEvent.Phase.START && worldTicks>=160) {
            Minecraft mc=Minecraft.getMinecraft();
            if(mc.thePlayer!=null) {
                // Turn the body under the unchanged native front camera to expose mounts.
                mc.thePlayer.renderYawOffset=mc.thePlayer.rotationYaw+(GripRendering.pose==0 ? -60 : 60);
                mc.thePlayer.prevRenderYawOffset=mc.thePlayer.renderYawOffset;
            }
        }
        if(event.phase==TickEvent.Phase.END && shot>=0) {
            Minecraft mc=Minecraft.getMinecraft();
            ScreenShotHelper.saveScreenshot(mc.mcDataDir,"s4b-grip-"+shot+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            shot=-1;
        }
    }
}
