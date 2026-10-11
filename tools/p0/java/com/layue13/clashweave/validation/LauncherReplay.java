package com.layue13.clashweave.validation;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import com.layue13.clashweave.client.ClientProxy;
import com.layue13.clashweave.core.Intent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
/** Sends real production intents; chat only marks isolated test intervals. */
public final class LauncherReplay {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final Set<String> requested=new HashSet<>();
    private boolean active;
    private int cases;
    public LauncherReplay(){MinecraftForge.EVENT_BUS.register(this);FMLCommonHandler.instance().bus().register(this);}
    @SubscribeEvent public void connected(cpw.mods.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent e){
        io.netty.channel.Channel channel=cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.network.NetworkManager.class,e.manager,"channel","field_150746_k");
        channel.pipeline().addBefore("packet_handler","p1_packet_observer",new io.netty.channel.ChannelInboundHandlerAdapter(){
            @Override public void channelRead(io.netty.channel.ChannelHandlerContext context,Object packet)throws Exception{
                if(packet instanceof net.minecraft.network.play.server.S12PacketEntityVelocity || packet instanceof net.minecraft.network.play.server.S08PacketPlayerPosLook)
                    System.out.println("P1_PACKET type="+packet.getClass().getSimpleName()+" nano="+System.nanoTime());
                super.channelRead(context,packet);
            }
        });
    }
    @SubscribeEvent public void chat(ClientChatReceivedEvent e){String s=e.message.getUnformattedText();if(s.startsWith("P1_BEGIN ")){active=true;requested.clear();cases++;System.out.println("P1_CLIENT_BEGIN "+s+" nano="+System.nanoTime());}if(s.startsWith("P1_END ")){active=false;System.out.println("P1_CLIENT_END "+s+" nano="+System.nanoTime());}}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END||!active||mc.thePlayer==null)return;
        ClientProxy p=ClientProxy.instance;ClientProxy.Visual v=p.own();if(v==null)return;
        if(mc.thePlayer.getCommandSenderName().equals("P0B")){mc.thePlayer.rotationYaw+=4;mc.thePlayer.motionX=0;mc.thePlayer.motionZ=0;mc.thePlayer.setPosition(0,mc.thePlayer.posY,1.8);return;}
        mc.thePlayer.rotationYaw=0;mc.thePlayer.rotationPitch=0;mc.thePlayer.motionX=0;mc.thePlayer.motionZ=0;mc.thePlayer.setPosition(0,mc.thePlayer.posY,0);
        String node=v.state.action;double age=p.elapsed(v,0);
        if(node.equals("iai"))requested.add("saw-iai");
        if(node.isEmpty() && !requested.contains("entry")){requested.add("entry");p.send(Intent.LIGHT,-1);}
        else if(node.equals("iai") && age>=17 && requested.add("after-iai"))p.send(Intent.LIGHT,-1);
        else if(node.isEmpty() && requested.contains("saw-iai") && requested.add("idle-light"))p.send(Intent.LIGHT,-1);
        else if(node.equals("light_1")&&age>=5&&requested.add("light_2"))p.send(Intent.LIGHT,-1);
        else if(node.equals("light_2")&&age>=6&&requested.add("launcher"))p.send(Intent.HEAVY,-1);
    }
}
