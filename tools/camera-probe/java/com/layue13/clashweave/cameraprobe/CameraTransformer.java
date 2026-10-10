package com.layue13.clashweave.cameraprobe;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
public final class CameraTransformer implements IClassTransformer {
 public byte[] transform(String name,String transformed,byte[] bytes){
  if(!Boolean.getBoolean("cw.p0.cameraProbe") || !transformed.equals("net.minecraft.client.renderer.EntityRenderer"))return bytes;
  ClassNode n=new ClassNode();new ClassReader(bytes).accept(n,0);int patched=0;
  for(Object object:n.methods){MethodNode m=(MethodNode)object;if(!(m.name.equals("orientCamera")||m.name.equals("func_78467_g"))||!m.desc.equals("(F)V"))continue;
   for(AbstractInsnNode ins:m.instructions.toArray())if(ins.getOpcode()==Opcodes.RETURN){InsnList call=new InsnList();call.add(new VarInsnNode(Opcodes.FLOAD,1));call.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"com/layue13/clashweave/cameraprobe/ProbeCamera","apply","(F)V",false));m.instructions.insertBefore(ins,call);patched++;}}
  if(patched!=1)throw new IllegalStateException("Camera probe hook mismatch: "+patched);
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);System.out.println("CAMERA_PROBE_PATCH orientCameraReturns="+patched);return w.toByteArray();
 }
}
