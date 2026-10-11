package com.layue13.clashweave.cameraprobe;
import java.util.Map;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({"com.layue13.clashweave.cameraprobe"})
public final class CameraPlugin implements IFMLLoadingPlugin {
 public String[] getASMTransformerClass(){return new String[]{"com.layue13.clashweave.cameraprobe.CameraTransformer"};}
 public String getModContainerClass(){return null;}public String getSetupClass(){return null;}public void injectData(Map<String,Object> data){}public String getAccessTransformerClass(){return null;}
}
