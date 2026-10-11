package com.layue13.clashweave.coremod;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Bracket vanilla orientation; OFF/first-person never changes its resulting matrix. */
public final class CameraTransform implements IClassTransformer {

    private static final String HOOK = "com/layue13/clashweave/client/SoulsCamera";

    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !"net.minecraft.client.renderer.EntityRenderer".equals(transformedName)) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int methods = 0, exits = 0;
        for (Object object : node.methods) {
            MethodNode method = (MethodNode) object;
            if (!method.desc.equals("(F)V")
                || !(method.name.equals("orientCamera") || method.name.equals("func_78467_g"))) continue;
            methods++;
            InsnList before = new InsnList();
            before.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "begin", "()V", false));
            method.instructions.insert(before);
            for (AbstractInsnNode instruction : method.instructions.toArray())
                if (instruction.getOpcode() == Opcodes.RETURN) {
                    InsnList after = new InsnList();
                    after.add(new VarInsnNode(Opcodes.FLOAD, 1));
                    after.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "finish", "(F)V", false));
                    method.instructions.insertBefore(instruction, after);
                    exits++;
                }
        }
        if (methods != 1 || exits != 1)
            throw new IllegalStateException("SOULS orientation layout changed: " + methods + "/" + exits);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        System.out.println("CW_SOULS_HOOK orientation=" + methods + " exits=" + exits);
        return writer.toByteArray();
    }
}
