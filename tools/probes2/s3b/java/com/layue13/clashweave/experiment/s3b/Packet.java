package com.layue13.clashweave.experiment.s3b;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
public class Packet implements IMessage {
    public int kind,id,lead,delay; public long tick,stamp,wall,send,local;
    public Packet() {}
    public Packet(int k,int i,int l,int d,long t,long s,long w,long sn,long lc) { kind=k;id=i;lead=l;delay=d;tick=t;stamp=s;wall=w;send=sn;local=lc; }
    public void fromBytes(ByteBuf b) { kind=b.readInt();id=b.readInt();lead=b.readInt();delay=b.readInt();tick=b.readLong();stamp=b.readLong();wall=b.readLong();send=b.readLong();local=b.readLong(); }
    public void toBytes(ByteBuf b) { b.writeInt(kind);b.writeInt(id);b.writeInt(lead);b.writeInt(delay);b.writeLong(tick);b.writeLong(stamp);b.writeLong(wall);b.writeLong(send);b.writeLong(local); }
}
