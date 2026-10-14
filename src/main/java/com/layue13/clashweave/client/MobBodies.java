package com.layue13.clashweave.client;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelSkeleton;
import net.minecraft.client.model.ModelZombie;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraftforge.client.event.RenderLivingEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Puts the body rig on the monsters that draw a plain ModelBiped (zombie, skeleton, zombie pigman). The first time a
 * renderer is seen its model is swapped for a {@link MobBiped} built from it; an entity wearing armor is drawn with the
 * original model, because the armor models are posed by vanilla's angles alone and would no longer follow the body.
 * Renderers whose model is something else (a mod's own class, a villager, a spider) are left alone.
 */
public final class MobBodies {

    private final Map<RenderBiped, MobBiped> swapped = new IdentityHashMap<>();
    private final Map<RenderBiped, Boolean> refused = new IdentityHashMap<>();

    private static Field mainModelField;

    /** RendererLivingEntity.mainModel is protected: found by type, as the one ModelBase the biped renderer shares. */
    private static ModelBase mainModel(RenderBiped renderer) {
        try {
            if (mainModelField == null) {
                for (Field field : RendererLivingEntity.class.getDeclaredFields()) {
                    if (field.getType() != ModelBase.class) continue;
                    field.setAccessible(true);
                    if (field.get(renderer) == renderer.modelBipedMain) mainModelField = field;
                }
                if (mainModelField == null) return null;
            }
            return (ModelBase) mainModelField.get(renderer);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void mainModel(RenderBiped renderer, ModelBiped model) {
        try {
            mainModel(renderer);
            mainModelField.set(renderer, model);
            renderer.modelBipedMain = model;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Whose arms vanilla keeps: the zombie's model holds them out in front by itself. */
    private static boolean armsVanilla(ModelBiped model) {
        return model instanceof ModelZombie;
    }

    @SubscribeEvent
    public void pre(RenderLivingEvent.Pre event) {
        if (!(event.renderer instanceof RenderBiped)) return;
        RenderBiped renderer = (RenderBiped) event.renderer;
        MobBiped body = swapped.get(renderer);
        if (body == null) {
            if (refused.containsKey(renderer)) return;
            ModelBiped model = renderer.modelBipedMain;
            Class<?> type = model.getClass();
            if (mainModel(renderer) != model
                || type != ModelZombie.class && type != ModelSkeleton.class && type != ModelBiped.class) {
                refused.put(renderer, Boolean.TRUE);
                return;
            }
            try {
                body = MobBiped.of(model, armsVanilla(model));
            } catch (RuntimeException e) {
                // A model this cannot cut stays as it is; the monster is drawn the way it always was.
                body = null;
            }
            if (body == null) {
                refused.put(renderer, Boolean.TRUE);
                return;
            }
            swapped.put(renderer, body);
        }
        boolean armored = false;
        for (int slot = 1; slot <= 4; slot++) armored |= event.entity.getEquipmentInSlot(slot) != null;
        if (armored) {
            body.release();
            mainModel(renderer, body.donor);
        } else {
            body.engage();
            mainModel(renderer, body);
        }
    }
}
