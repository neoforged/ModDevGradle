package jijtest;

import jijtestplugin.Plugin;
import net.neoforged.fml.classloading.transformation.TransformingClassLoader;
import net.neoforged.fml.common.Mod;

@Mod("jijtest")
public class AccessPluginClass {
    public AccessPluginClass() {
        // Validate that Plugin.class is not loaded via the transforming classloader
        if (Plugin.class.getClassLoader() instanceof TransformingClassLoader) {
            throw new IllegalStateException("Expected Plugin to be loaded as a plugin!");
        }
    }
}
