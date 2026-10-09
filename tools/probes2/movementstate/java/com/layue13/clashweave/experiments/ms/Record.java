package com.layue13.clashweave.experiments.ms;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class Record implements IMessage {
    public int kind, id;
    public long tick, nano;
    public double x, y, z, a, b;
    public Record() {}
    public Record(int kind, int id, long tick, double x, double y, double z, double a, double b) {
        this.kind = kind; this.id = id; this.tick = tick; this.nano = System.nanoTime();
        this.x = x; this.y = y; this.z = z; this.a = a; this.b = b;
    }
    public void toBytes(ByteBuf out) {
        out.writeInt(kind); out.writeInt(id); out.writeLong(tick); out.writeLong(nano);
        out.writeDouble(x); out.writeDouble(y); out.writeDouble(z); out.writeDouble(a); out.writeDouble(b);
    }
    public void fromBytes(ByteBuf in) {
        if (in.readableBytes() != 64) throw new IllegalArgumentException("record size");
        kind=in.readInt(); id=in.readInt(); tick=in.readLong(); nano=in.readLong();
        x=in.readDouble(); y=in.readDouble(); z=in.readDouble(); a=in.readDouble(); b=in.readDouble();
    }
}
