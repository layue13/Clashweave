package com.layue13.clashweave.coremod;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/** Does not resolve any client classes on the dedicated server. */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({ "com.layue13.clashweave.coremod" })
public final class CameraLoadingPlugin implements IFMLLoadingPlugin {

    public String[] getASMTransformerClass() {
        return new String[] { CameraTransform.class.getName() };
    }

    public String getModContainerClass() {
        return null;
    }

    public String getSetupClass() {
        return null;
    }

    public String getAccessTransformerClass() {
        return null;
    }

    public void injectData(Map<String, Object> data) {}
}
