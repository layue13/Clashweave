package com.layue13.clashweave.client;

import static org.junit.Assert.*;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelSkeleton;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;

import org.junit.Test;

/** Cutting a limb at its joint must keep every face inside its piece and every texture coordinate on the skin. */
public class LimbSlicerTest {

    private static void check(ModelBiped model, ModelRenderer part, float fraction) {
        ModelBox before = (ModelBox) part.cubeList.get(0);
        float y1 = before.posY1, y2 = before.posY2, cut = y1 + fraction * (y2 - y1);
        LimbSlicer.Joint joint = LimbSlicer.slice(model, part, fraction);
        assertNotNull(joint);
        assertTrue(part.childModels.contains(joint.lower));
        assertTrue(part.childModels.contains(joint.prism));
        for (TexturedQuad quad : LimbSlicer.quadsOf((ModelBox) part.cubeList.get(0))) {
            for (PositionTextureVertex v : quad.vertexPositions) {
                assertTrue("upper piece stays above the joint", v.vector3D.yCoord <= cut + 1e-4);
                uvOnSkin(v);
            }
        }
        for (TexturedQuad quad : LimbSlicer.quadsOf((ModelBox) joint.lower.cubeList.get(0))) {
            for (PositionTextureVertex v : quad.vertexPositions) {
                assertTrue("lower piece is measured from the joint", v.vector3D.yCoord >= -1e-4);
                assertTrue(v.vector3D.yCoord <= y2 - cut + 1e-4);
                uvOnSkin(v);
            }
        }
        assertEquals(14, LimbSlicer.quadsOf((ModelBox) joint.prism.cubeList.get(0)).length);
        for (TexturedQuad quad : LimbSlicer.quadsOf((ModelBox) joint.prism.cubeList.get(0)))
            for (PositionTextureVertex v : quad.vertexPositions) uvOnSkin(v);
    }

    private static void uvOnSkin(PositionTextureVertex v) {
        assertTrue(v.texturePositionX >= -1e-3f && v.texturePositionX <= 1 + 1e-3f);
        assertTrue(v.texturePositionY >= -1e-3f && v.texturePositionY <= 1 + 1e-3f);
    }

    @Test
    public void aBipedsFourLimbsCutCleanly() {
        ModelBiped model = new ModelBiped(0);
        check(model, model.bipedRightArm, .583f);
        check(model, model.bipedLeftArm, .583f);
        check(model, model.bipedRightLeg, .5f);
        check(model, model.bipedLeftLeg, .5f);
    }

    @Test
    public void aSkeletonsThinLimbsCutCleanly() {
        ModelBiped model = new ModelSkeleton();
        check(model, model.bipedRightArm, .583f);
        check(model, model.bipedLeftLeg, .5f);
    }

    @Test
    public void aPartThatIsNotASingleTallBoxIsLeftAlone() {
        ModelBiped model = new ModelBiped(0);
        ModelRenderer stub = new ModelRenderer(model, 0, 0);
        stub.addBox(0, 0, 0, 4, 4, 4);
        assertNull(LimbSlicer.slice(model, stub, .5f));
        ModelRenderer two = new ModelRenderer(model, 0, 0);
        two.addBox(0, 0, 0, 4, 12, 4);
        two.addBox(0, 12, 0, 4, 4, 4);
        assertNull(LimbSlicer.slice(model, two, .5f));
    }
}
