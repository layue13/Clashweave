package com.layue13.clashweave.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import org.apache.logging.log4j.Logger;

import com.layue13.clashweave.content.ContentPack;
import com.layue13.clashweave.content.ContentRegistry;
import com.layue13.clashweave.core.ActionCatalog;

/**
 * Presentation data read from the content registry once, at load time: one Render per resource ID with its models,
 * texture, grip points and mounts, and one Preset per animation ID. A frame only follows references that were
 * resolved when the entity's state arrived; nothing here parses, concatenates or loads while rendering.
 */
final class PresentationAssets {

    static final String MOUNT_SLOT = "left_hip";

    /** Hand space is Biped: block units, +Y down the arm. Immutable after load. */
    static final class Render {

        final String id;
        final IModelCustom held, stowed, stowedEmpty;
        final ResourceLocation texture;
        final double gripX, gripY, gripZ;
        final double rightX, rightY, rightZ;
        /** Read from the data; the rigid adapter has no left-hand pose to apply it to yet. */
        final double leftX, leftY, leftZ;
        final boolean mounted;
        final double mountX, mountY, mountZ, mountAngle;

        Render(String id, IModelCustom held, IModelCustom stowed, IModelCustom stowedEmpty, ContentPack.Resource data,
            ResourceLocation texture) {
            this.id = id;
            this.held = held;
            this.stowed = stowed;
            this.stowedEmpty = stowedEmpty;
            this.texture = texture;
            gripX = data.grip.offset.x;
            gripY = data.grip.offset.y;
            gripZ = data.grip.offset.z;
            rightX = data.handGrips.right.x;
            rightY = data.handGrips.right.y;
            rightZ = data.handGrips.right.z;
            leftX = data.handGrips.left.x;
            leftY = data.handGrips.left.y;
            leftZ = data.handGrips.left.z;
            ContentPack.Mount mount = null;
            for (ContentPack.Mount candidate : data.mounts) if (MOUNT_SLOT.equals(candidate.slot)) {
                mount = candidate;
                break;
            }
            mounted = mount != null;
            mountX = mount == null ? 0 : mount.x;
            mountY = mount == null ? 0 : mount.y;
            mountZ = mount == null ? 0 : mount.z;
            mountAngle = mount == null ? 0 : mount.angle;
        }
    }

    /** Procedural pose selected by the animation's preset name. */
    interface Preset {

        /** Degrees about the swing axis. */
        double swing(ActionCatalog.Definition definition, double elapsed);

        /** How long the drawn blade stays visible while an action that returns to the sheath plays. */
        int sheatheVisibleTicks();
    }

    private static final class RigidArc implements Preset {

        @Override
        public double swing(ActionCatalog.Definition definition, double elapsed) {
            if (elapsed < definition.startup) return -definition.arc / 2 * elapsed / Math.max(1, definition.startup);
            if (elapsed < definition.startup + definition.active) return -definition.arc / 2
                + definition.arc * (elapsed - definition.startup) / Math.max(1, definition.active);
            return definition.arc / 2
                * Math.max(0, 1 - (elapsed - definition.startup - definition.active) / definition.recovery);
        }

        @Override
        public int sheatheVisibleTicks() {
            return 6;
        }
    }

    static final String DEFAULT_PRESET = "rigid_arc";
    private static final Map<String, Preset> PRESETS = new HashMap<>();
    private static final Map<String, Render> renders = new HashMap<>();
    private static final Map<String, Preset> presets = new HashMap<>();
    private static Render fallbackRender;
    private static Preset fallbackPreset;

    static {
        PRESETS.put(DEFAULT_PRESET, new RigidArc());
    }

    private PresentationAssets() {}

    /** Loads every resource's models once. A missing file names the resource that referenced it. */
    static void load(ContentRegistry registry, Logger log) {
        renders.clear();
        presets.clear();
        fallbackRender = null;
        Map<String, IModelCustom> models = new HashMap<>();
        for (ContentPack pack : registry.packs()
            .values()) {
            for (Map.Entry<String, ContentPack.Resource> entry : pack.resources()
                .entrySet()) {
                ContentPack.Resource data = entry.getValue();
                String id = entry.getKey();
                Render render = new Render(
                    id,
                    model(models, id, "held", data.models.held),
                    model(models, id, "stowed", data.models.stowed),
                    model(models, id, "stowed_empty", data.models.stowed_empty),
                    data,
                    location(id, "texture", data.texture));
                renders.put(id, render);
                if (fallbackRender == null || id.equals("katana:default")) fallbackRender = render;
            }
            for (ContentPack.Animation animation : pack.animations()
                .values()) {
                Preset preset = PRESETS.get(animation.preset);
                if (preset == null) {
                    log.warn(
                        "Clashweave: animation {} names unknown preset \"{}\"; using {}",
                        animation.id,
                        animation.preset,
                        DEFAULT_PRESET);
                    preset = PRESETS.get(DEFAULT_PRESET);
                }
                presets.put(animation.id, preset);
            }
        }
        fallbackPreset = PRESETS.get(DEFAULT_PRESET);
        log.info("Clashweave presentation: {} render(s), {} animation(s)", renders.size(), presets.size());
    }

    private static IModelCustom model(Map<String, IModelCustom> cache, String resource, String field, String path) {
        IModelCustom model = cache.get(path);
        if (model == null) {
            try {
                model = AdvancedModelLoader.loadModel(location(resource, "models." + field, path));
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                    "resource " + resource + " models." + field + " could not be loaded from " + path + ": " + e,
                    e);
            }
            cache.put(path, model);
        }
        return model;
    }

    private static ResourceLocation location(String resource, String field, String text) {
        int colon = text.indexOf(':');
        if (colon <= 0 || colon == text.length() - 1) throw new IllegalStateException(
            "resource " + resource + " " + field + ": expected domain:path, got " + text);
        return new ResourceLocation(text.substring(0, colon), text.substring(colon + 1));
    }

    /** Binds every texture once so the first frame that shows a weapon does not decode a PNG. */
    static void warm(TextureManager textures) {
        for (Render render : renders.values()) textures.bindTexture(render.texture);
    }

    /** One HashMap probe on a String whose hash is cached; called when a state arrives, not per frame. */
    static Render render(String skin) {
        Render render = renders.get(skin);
        return render == null ? fallbackRender : render;
    }

    static Preset preset(String animation) {
        Preset preset = presets.get(animation);
        return preset == null ? fallbackPreset : preset;
    }

    static List<String> ids() {
        return new java.util.ArrayList<>(renders.keySet());
    }
}
