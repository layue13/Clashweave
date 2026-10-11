package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.layue13.clashweave.coremod.CameraTransform;

public class CameraTransformTest {

    private byte[] example(String method, boolean two) {
        ClassWriter w = new ClassWriter(0);
        w.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "Example", null, "java/lang/Object", null);
        org.objectweb.asm.MethodVisitor m = w.visitMethod(Opcodes.ACC_PRIVATE, method, "(F)V", null, null);
        m.visitCode();
        m.visitInsn(Opcodes.RETURN);
        if (two) m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 2);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    @Test
    public void onlyRendererOrientationGetsEntryAndExitHooks() {
        for (String name : new String[] { "orientCamera", "func_78467_g" }) {
            byte[] old = example(name, false);
            CameraTransform t = new CameraTransform();
            assertSame(old, t.transform("Example", "net.minecraft.server.MinecraftServer", old));
            byte[] result = t.transform("Example", "net.minecraft.client.renderer.EntityRenderer", old);
            ClassNode n = new ClassNode();
            new ClassReader(result).accept(n, 0);
            int begins = 0, ends = 0;
            for (Object o : n.methods)
                for (AbstractInsnNode i : ((MethodNode) o).instructions.toArray()) if (i instanceof MethodInsnNode) {
                    MethodInsnNode c = (MethodInsnNode) i;
                    if (c.name.equals("begin")) begins++;
                    if (c.name.equals("finish")) ends++;
                }
            assertEquals(1, begins);
            assertEquals(1, ends);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void changedLayoutFailsLoudlyInsteadOfApplyingPartialCamera() {
        new CameraTransform()
            .transform("Example", "net.minecraft.client.renderer.EntityRenderer", example("orientCamera", true));
    }
}
