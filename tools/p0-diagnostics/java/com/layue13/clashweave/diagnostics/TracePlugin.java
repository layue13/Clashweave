package com.layue13.clashweave.diagnostics;

import java.util.Map;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/** Validation jar only. No Minecraft/client classes resolve on the server. */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1002)
@IFMLLoadingPlugin.TransformerExclusions({"com.layue13.clashweave.diagnostics"})
public final class TracePlugin implements IFMLLoadingPlugin {
    public String[] getASMTransformerClass(){return new String[]{TraceTransform.class.getName()};}
    public String getModContainerClass(){return null;}
    public String getSetupClass(){return null;}
    public String getAccessTransformerClass(){return null;}
    public void injectData(Map<String,Object> data){}
}
