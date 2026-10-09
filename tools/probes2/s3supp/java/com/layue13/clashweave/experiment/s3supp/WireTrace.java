package com.layue13.clashweave.experiment.s3supp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;

/** Capture connected fixture-channel intents before the vanilla queue; no world or packet mutation. */
public class WireTrace {
    static final Object guardLock=new Object();
    static final java.util.Map<Integer,Seen> inputs=new java.util.HashMap<Integer,Seen>();
    static class Seen { final Packet packet;final long nano;Seen(Packet p,long n){packet=p;nano=n;} }

    public static void attach(NetworkManager manager,final String side,final boolean defender) {
        try {
            java.lang.reflect.Field f=cpw.mods.fml.relauncher.ReflectionHelper.findField(NetworkManager.class,"channel","field_150746_k");
            Channel c=(Channel)f.get(manager);
            c.pipeline().addBefore("packet_handler","cw_s3supp_trace",new ChannelInboundHandlerAdapter(){
                public void channelRead(ChannelHandlerContext ctx,Object msg)throws Exception {
                    String channel=null;ByteBuf b=null;
                    if(msg instanceof C17PacketCustomPayload){C17PacketCustomPayload p=(C17PacketCustomPayload)msg;channel=p.func_149559_c();if(p.func_149558_e()!=null)b=Unpooled.wrappedBuffer(p.func_149558_e());}
                    if(msg instanceof S3FPacketCustomPayload){S3FPacketCustomPayload p=(S3FPacketCustomPayload)msg;channel=p.func_149169_c();if(p.func_149168_d()!=null)b=Unpooled.wrappedBuffer(p.func_149168_d());}
                    if(msg instanceof FMLProxyPacket){FMLProxyPacket p=(FMLProxyPacket)msg;channel=p.channel();b=p.payload().duplicate();}
                    if("cw_s3supp".equals(channel)&&b!=null&&b.readableBytes()>=57){
                        b.readByte();int kind=b.readInt(),id=b.readInt();
                        long nano=System.nanoTime();
                        if("SERVER".equals(side)&&defender&&kind==2){
                            ByteBuf copy=b.duplicate();copy.readerIndex(b.readerIndex()-8);Packet decoded=new Packet();decoded.fromBytes(copy);
                            synchronized(guardLock){nano=System.nanoTime();inputs.put(id,new Seen(decoded,nano));}
                        }
                        Experiment.log("WIRE nano="+nano+" side="+side+" kind="+kind+" id="+id+" wall="+System.currentTimeMillis()+" thread="+Thread.currentThread().getName()+" observed="+Server.observedTick);
                    }
                    super.channelRead(ctx,msg);
                }
            });
        } catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
}
