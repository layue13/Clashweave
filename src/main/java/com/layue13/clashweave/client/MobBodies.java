package com.layue13.clashweave.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelEnderman;
import net.minecraft.client.model.ModelSkeleton;
import net.minecraft.client.model.ModelZombie;
import net.minecraft.client.model.ModelZombieVillager;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderEnderman;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraftforge.client.event.RenderLivingEvent;

import com.layue13.clashweave.client.body.Gait;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Puts the body rig on the monsters that draw a plain ModelBiped (zombie, skeleton, zombie pigman). The first time a
 * renderer is seen its model is swapped for a {@link MobBiped} built from it; an entity wearing armor is drawn with the
 * original model, because the armor models are posed by vanilla's angles alone and would no longer follow the body.
 * Renderers whose model is something else (a mod's own class, a villager, a spider) are left alone.
 */
public final class MobBodies {

    /** Keyed by the model a renderer draws with, not by the renderer: RenderZombie switches between two. */
    private final Map<ModelBiped, MobBiped> byDonor = new IdentityHashMap<>();
    private final Map<ModelBiped, Boolean> refused = new IdentityHashMap<>();
    private final Map<MobBiped, MobVillagerShell> shells = new IdentityHashMap<>();
    private final Map<RenderBiped, CombatBiped[]> followers = new IdentityHashMap<>();
    private final Map<RenderBiped, Boolean> noFollowers = new IdentityHashMap<>();

    /** Half-extra width of a model's head box over the plain 8 px: 0 for skin, 1 or 0.5 for the two armor models. */
    private static float expandOf(ModelBiped model) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (TexturedQuad quad : LimbSlicer.quadsOf((ModelBox) model.bipedHead.cubeList.get(0)))
            for (PositionTextureVertex v : quad.vertexPositions) {
                min = Math.min(min, v.vector3D.xCoord);
                max = Math.max(max, v.vector3D.xCoord);
            }
        return (float) (max - min - 8) / 2;
    }

    /**
     * Replaces the renderer's two armor models (plain ModelBiped, 1.0 and 0.5) by ones that follow the body rig, as a
     * player's armor does. Returns null, and leaves them alone, when the renderer holds anything else there.
     */
    private CombatBiped[] followersOf(RenderBiped renderer, MobBiped body) {
        CombatBiped[] known = followers.get(renderer);
        if (known != null) return known;
        if (noFollowers.containsKey(renderer)) return null;
        try {
            java.util.List<Field> armor = new java.util.ArrayList<>();
            for (Field field : RenderBiped.class.getDeclaredFields()) {
                if (field.getType() != ModelBiped.class || Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object value = field.get(renderer);
                if (value == body || value == body.donor || value == null || value instanceof MobVillagerShell)
                    continue;
                if (value.getClass() != ModelBiped.class) {
                    armor.clear();
                    break;
                }
                armor.add(field);
            }
            if (armor.size() != 2) {
                noFollowers.put(renderer, Boolean.TRUE);
                return null;
            }
            CombatBiped[] made = new CombatBiped[2];
            for (Field field : armor) {
                float expand = expandOf((ModelBiped) field.get(renderer));
                CombatBiped follower = new CombatBiped(expand > .75f ? 1.0f : .5f);
                field.set(renderer, follower);
                made[expand > .75f ? 0 : 1] = follower;
            }
            if (made[0] == null || made[1] == null) {
                noFollowers.put(renderer, Boolean.TRUE);
                return null;
            }
            followers.put(renderer, made);
            return made;
        } catch (IllegalAccessException | RuntimeException e) {
            noFollowers.put(renderer, Boolean.TRUE);
            return null;
        }
    }

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

    /**
     * RenderZombie puts its own model back as mainModel in shouldRenderPass, between the draw and the hurt flash, so
     * the flash would be drawn with the original, re-posed by vanilla and not matching the first pass (depth-equal).
     * Its normal-zombie model lives in a ModelBiped field and its villager model in a ModelZombieVillager one:
     * pointing each at ours (the villager's through {@link MobVillagerShell}) keeps ours in place.
     */
    private static void keepOnZombieRenderer(RenderBiped renderer, MobBiped body, ModelBiped installed) {
        if (!(renderer instanceof net.minecraft.client.renderer.entity.RenderZombie)) return;
        try {
            for (Field field : net.minecraft.client.renderer.entity.RenderZombie.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                boolean plain = field.getType() == ModelBiped.class;
                boolean villager = field.getType() == ModelZombieVillager.class;
                if (!plain && !villager) continue;
                field.setAccessible(true);
                if (field.get(renderer) == body.donor && field.getType()
                    .isInstance(installed)) field.set(renderer, installed);
            }
        } catch (IllegalAccessException | RuntimeException e) {
            // Left as it is: the flash is then drawn with the original model.
        }
    }

    /** The models this takes over: exactly these classes, never a subclass (a mod's own model keeps its own posing). */
    static boolean accepts(Class<?> type) {
        return type == ModelZombie.class || type == ModelSkeleton.class
            || type == ModelBiped.class
            || type == ModelZombieVillager.class;
    }

    /** Whose arms vanilla keeps: the zombie's model holds them out in front by itself. */
    private static boolean armsVanilla(ModelBiped model) {
        return model instanceof ModelZombie || model instanceof ModelZombieVillager;
    }

    /** RendererLivingEntity.mainModel by type: the ModelBase the renderer draws with. */
    private static ModelBase mainModelOf(RendererLivingEntity renderer) {
        try {
            if (livingModelField == null) {
                for (Field field : RendererLivingEntity.class.getDeclaredFields()) {
                    if (field.getType() != ModelBase.class) continue;
                    field.setAccessible(true);
                    // mainModel is declared before renderPassModel, which is empty until a pass sets it.
                    if (field.get(renderer) == null) continue;
                    livingModelField = field;
                    break;
                }
                if (livingModelField == null) return null;
            }
            return (ModelBase) livingModelField.get(renderer);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field livingModelField;

    /**
     * The enderman: its renderer keeps the model for the eyes and the carried block, so only the main model is
     * swapped; a model that is not the plain vanilla one (a mod's) stays.
     */
    private void enderman(RendererLivingEntity renderer) {
        ModelBase current = mainModelOf(renderer);
        if (current instanceof MobBiped || current == null || current.getClass() != ModelEnderman.class) return;
        ModelEnderman donor = (ModelEnderman) current;
        MobBiped body = byDonor.get(donor);
        if (body == null) {
            if (refused.containsKey(donor)) return;
            try {
                body = MobBiped.ofEnderman(donor);
            } catch (RuntimeException e) {
                body = null;
            }
            if (body == null) {
                refused.put(donor, Boolean.TRUE);
                return;
            }
            byDonor.put(donor, body);
        }
        body.engage();
        try {
            livingModelField.set(renderer, body);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SubscribeEvent
    public void pre(RenderLivingEvent.Pre event) {
        if (event.renderer instanceof RenderEnderman) {
            enderman(event.renderer);
            return;
        }
        if (!(event.renderer instanceof RenderBiped)) return;
        RenderBiped renderer = (RenderBiped) event.renderer;
        ModelBiped current = renderer.modelBipedMain;
        if (mainModel(renderer) != current) return;
        MobBiped body;
        if (current instanceof MobVillagerShell) body = ((MobVillagerShell) current).core;
        else if (current instanceof MobBiped) body = (MobBiped) current;
        else {
            body = byDonor.get(current);
            if (body == null) {
                if (refused.containsKey(current)) return;
                if (accepts(current.getClass())) {
                    try {
                        body = MobBiped.of(
                            current,
                            armsVanilla(current),
                            current instanceof ModelSkeleton ? Gait.SKELETON : Gait.ZOMBIE);
                    } catch (RuntimeException e) {
                        // A model this cannot cut stays as it is; the monster is drawn the way it always was.
                        body = null;
                    }
                }
                if (body == null) {
                    refused.put(current, Boolean.TRUE);
                    return;
                }
                byDonor.put(current, body);
            }
        }
        boolean armored = false;
        for (int slot = 1; slot <= 4; slot++) armored |= event.entity.getEquipmentInSlot(slot) != null;
        body.rigidArms = event.entity.getEquipmentInSlot(3) != null;
        body.rigidLegs = event.entity.getEquipmentInSlot(2) != null || event.entity.getEquipmentInSlot(1) != null;
        CombatBiped[] worn = armored ? followersOf(renderer, body) : null;
        if (armored && worn == null) {
            // Armor models this cannot make follow the body: draw the monster as it always was.
            body.release();
            mainModel(renderer, body.donor);
            return;
        }
        body.engage();
        ModelBiped installed = body;
        if (body.donor instanceof ModelZombieVillager) {
            MobVillagerShell shell = shells.get(body);
            if (shell == null) {
                shell = new MobVillagerShell(body);
                shells.put(body, shell);
            }
            installed = shell;
        }
        mainModel(renderer, installed);
        keepOnZombieRenderer(renderer, body, installed);
        if (worn != null) {
            long now = System.nanoTime();
            com.layue13.clashweave.client.body.BodyRig rig = BodyRigs.of(event.entity.getEntityId(), now);
            for (CombatBiped follower : worn) {
                follower.rig = rig;
                follower.frameNanos = now;
                follower.tickPeriod = ClientProxy.instance == null ? 50_000_000L : ClientProxy.instance.tickPeriod();
                follower.locomotion = false;
            }
        }
    }
}
