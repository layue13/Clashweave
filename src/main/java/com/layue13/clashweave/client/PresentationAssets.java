package com.layue13.clashweave.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import com.layue13.clashweave.core.AnimationLookup;
import com.layue13.clashweave.core.AppearanceState;

/** P0 registers one appearance and one procedural animation. IDs cannot change gameplay. */
final class PresentationAssets {

    static final class Models {

        final IModelCustom blade, sheath;
        final ResourceLocation texture;

        Models() {
            blade = AdvancedModelLoader.loadModel(new ResourceLocation("clashweave", "models/katana.obj"));
            sheath = AdvancedModelLoader.loadModel(new ResourceLocation("clashweave", "models/sheath.obj"));
            texture = new ResourceLocation("clashweave", "textures/models/katana.png");
        }
    }

    private static final Map<String, Models> models = new HashMap<>();
    private static final AnimationLookup animations = new AnimationLookup();

    static void load() {
        models.put(AppearanceState.DEFAULT.skin, new Models());
        for (String action : new String[] { "iai", "light_1", "light_2", "light_3", "heavy", "sheathe" })
            animations.put(AppearanceState.DEFAULT.animations, action, "rigid_arc");
        animations.put("type:katana", "*", "rigid_arc");
        animations.put("global", "*", "rigid_arc");
    }

    static Models models(ClientProxy.Visual visual) {
        String skin = visual == null ? AppearanceState.DEFAULT.skin : visual.state.appearance.skin;
        return models.containsKey(skin) ? models.get(skin) : models.get(AppearanceState.DEFAULT.skin);
    }

    static String animation(ClientProxy.Visual visual, String action) {
        return animations.resolve(visual.state.appearance.animations, "type:" + visual.state.style, action);
    }
}
