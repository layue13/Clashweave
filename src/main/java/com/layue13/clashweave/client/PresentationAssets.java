package com.layue13.clashweave.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import org.apache.logging.log4j.Logger;

import com.layue13.clashweave.client.pose.Track;
import com.layue13.clashweave.client.pose.TrackData;
import com.layue13.clashweave.content.ContentPack;
import com.layue13.clashweave.content.ContentRegistry;
import com.layue13.clashweave.core.ActionCatalog;

/**
 * Presentation data read from the content registry once, at load time: one Render per resource ID with its models,
 * texture, grip points and mounts, and one Preset per animation ID. A frame only follows references that were
 * resolved when the entity's state arrived; nothing here parses, concatenates or loads while rendering.
 */
final class PresentationAssets {

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
        /** The anchor the stowed model hangs on (a {@link com.layue13.clashweave.core.MountSlots} name). */
        final String mountSlot;
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
            mountSlot = data.stowedSlot == null ? com.layue13.clashweave.core.MountSlots.DEFAULT : data.stowedSlot;
            ContentPack.Mount mount = null;
            for (ContentPack.Mount candidate : data.mounts) if (mountSlot.equals(candidate.slot)) {
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

        /** Baked track of an action by local ID (no namespace), or null when this preset poses the body no further. */
        default Track track(String localActionId) {
            return null;
        }

        /** Track of the engaged idle pose, or null. */
        default Track stance() {
            return null;
        }
    }

    private static class RigidArc implements Preset {

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

    /** The rigid swing plus baked body tracks: the tracks pose arms, legs and torso, the swing remains the fallback. */
    private static final class TrackPreset extends RigidArc {

        private final TrackData data;
        private final Track stance;

        TrackPreset(TrackData data, String stanceAction) {
            this.data = data;
            stance = stanceAction == null ? null : data.track(stanceAction);
        }

        @Override
        public Track track(String localActionId) {
            return data.track(localActionId);
        }

        @Override
        public Track stance() {
            return stance;
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
        // Sorted, so which render stands in for an unknown skin does not depend on pack load order.
        Map<String, ContentPack.Resource> resources = new java.util.TreeMap<>();
        for (ContentPack pack : registry.packs()
            .values()) {
            resources.putAll(pack.resources());
            for (ContentPack.Animation animation : pack.animations()
                .values()) {
                Preset preset = "track".equals(animation.preset) ? trackPreset(animation)
                    : PRESETS.get(animation.preset);
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
        for (Map.Entry<String, ContentPack.Resource> entry : resources.entrySet()) {
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
            if (fallbackRender == null) fallbackRender = render;
        }
        fallbackPreset = PRESETS.get(DEFAULT_PRESET);
        log.info("Clashweave presentation: {} render(s), {} animation(s)", renders.size(), presets.size());
    }

    /** Tracks are read from the classpath, like the content packs; a bad file names the animation that wanted it. */
    private static Preset trackPreset(ContentPack.Animation animation) {
        String source = animation.source;
        int colon = source.indexOf(':');
        String path = "/assets/" + source.substring(0, colon) + "/" + source.substring(colon + 1);
        try (java.io.InputStream stream = PresentationAssets.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("file not found");
            TrackData data = TrackData
                .parse(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            if (animation.stance != null && !data.has(animation.stance))
                throw new IllegalStateException("stance action " + animation.stance + " has no track");
            return new TrackPreset(data, animation.stance);
        } catch (java.io.IOException | RuntimeException e) {
            throw new IllegalStateException(
                "animation " + animation.id + " source " + source + " could not be loaded: " + e,
                e);
        }
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
