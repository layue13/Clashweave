package com.layue13.clashweave.probe;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** Fixed-size probe wire record; intentionally no public protocol commitment. */
public class ProbePacket implements IMessage {

    public int kind;
    public int sequence;
    public long tick;
    public double x;
    public double z;

    public ProbePacket() {}

    public ProbePacket(int kind, int sequence, long tick, double x, double z) {
        this.kind = kind;
        this.sequence = sequence;
        this.tick = tick;
        this.x = x;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf b) {
        if (b.readableBytes() != 32) throw new IllegalArgumentException("probe packet length");
        kind = b.readInt();
        sequence = b.readInt();
        tick = b.readLong();
        x = b.readDouble();
        z = b.readDouble();
    }

    @Override
    public void toBytes(ByteBuf b) {
        b.writeInt(kind);
        b.writeInt(sequence);
        b.writeLong(tick);
        b.writeDouble(x);
        b.writeDouble(z);
    }
}
