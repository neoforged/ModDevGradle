package mymod;

import net.minecraft.DetectedVersion;
import net.minecraftforge.fml.common.Mod;

@Mod("mymod")
public class MyMod {
    public void run() {
        DetectedVersion.tryDetectVersion();
    }

    @javax.annotation.Nullable
    private static Object javaxNullableTest() {
        return null;
    }

    @org.jetbrains.annotations.Nullable
    private static Object jetbrainsNullableTest() {
        return null;
    }
}
