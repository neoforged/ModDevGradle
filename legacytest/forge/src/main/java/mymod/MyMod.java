package mymod;

import net.minecraft.DetectedVersion;
import net.minecraftforge.fml.common.Mod;

@Mod("mymod")
public class MyMod {
    public void run() {
        DetectedVersion.tryDetectVersion();
    }
}
