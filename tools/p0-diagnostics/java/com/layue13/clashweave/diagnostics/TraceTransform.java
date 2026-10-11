package com.layue13.clashweave.diagnostics;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Adds observation calls only, without rewriting a branch, field assignment or return value. */
public final class TraceTransform implements IClassTransformer {
    private static final String HOOK="com/layue13/clashweave/diagnostics/TraceObserver";
    private static MethodInsnNode call(String name,String desc){return new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,name,desc,false);}
    public byte[] transform(String name,String transformedName,byte[] bytes){
        if(bytes==null || !Boolean.getBoolean("clashweave.trace"))return bytes;
        boolean camera=transformedName.equals("com.layue13.clashweave.core.CameraBearing");
        boolean scheduler=transformedName.equals("com.layue13.clashweave.core.Scheduler");
        boolean server=transformedName.equals("com.layue13.clashweave.forge.CombatServer");
        boolean client=transformedName.equals("com.layue13.clashweave.client.ClientProxy");
        if(!camera && !scheduler && !server && !client)return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        boolean modern=false;for(Object item:node.methods)if(((MethodNode)item).name.equals("lead") && Type.getArgumentTypes(((MethodNode)item).desc).length==10)modern=true;
        int patched=0;
        for(Object item:node.methods){MethodNode m=(MethodNode)item;
            if(camera && m.name.equals("lead") && Type.getArgumentTypes(m.desc).length==(modern?10:8)){
                InsnList in=new InsnList();in.add(new VarInsnNode(Opcodes.ALOAD,0));
                for(int i=0;i<(modern?10:8);i++)in.add(new VarInsnNode(Opcodes.DLOAD,1+i*2));
                if(!modern){in.add(new LdcInsnNode(.1));in.add(new LdcInsnNode(.3));}
                in.add(new InsnNode(modern?Opcodes.ICONST_1:Opcodes.ICONST_0));
                in.add(call("cameraBefore","(Ljava/lang/Object;DDDDDDDDDDZ)V"));m.instructions.insert(in);
                for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.DRETURN){
                    InsnList out=new InsnList();out.add(new InsnNode(Opcodes.DUP2));out.add(new VarInsnNode(Opcodes.ALOAD,0));out.add(call("cameraAfter","(DLjava/lang/Object;)V"));m.instructions.insertBefore(n,out);
                }patched++;
            }
            if(scheduler && m.name.equals("tick") && m.desc.equals("(J)V")){
                InsnList in=new InsnList();in.add(new VarInsnNode(Opcodes.ALOAD,0));in.add(new VarInsnNode(Opcodes.LLOAD,1));in.add(call("schedulerBefore","(Ljava/lang/Object;J)V"));m.instructions.insert(in);
                for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){InsnList out=new InsnList();out.add(new VarInsnNode(Opcodes.ALOAD,0));out.add(call("schedulerAfter","(Ljava/lang/Object;)V"));m.instructions.insertBefore(n,out);}patched++;
            }
            if(server && m.name.equals("consume")){
                InsnList in=new InsnList();in.add(new VarInsnNode(Opcodes.ALOAD,0));in.add(new VarInsnNode(Opcodes.ALOAD,1));in.add(call("consume","(Ljava/lang/Object;Ljava/lang/Object;)V"));m.instructions.insert(in);patched++;
            }
            if(client && m.name.equals("send")){
                for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode && ((MethodInsnNode)n).name.equals("sendToServer")){
                    InsnList in=new InsnList();in.add(new InsnNode(Opcodes.DUP));in.add(new VarInsnNode(Opcodes.ALOAD,0));in.add(call("press","(Ljava/lang/Object;Ljava/lang/Object;)V"));m.instructions.insertBefore(n,in);patched++;
                }
            }
        }
        if(patched!=1)throw new IllegalStateException("Trace layout changed: "+transformedName+" hooks="+patched);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);
        System.out.println("P0_TRACE_HOOK class="+transformedName+" hooks="+patched+" traceOnly=true");return writer.toByteArray();
    }
}
