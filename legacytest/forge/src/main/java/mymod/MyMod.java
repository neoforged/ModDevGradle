package mymod;

import net.minecraft.DetectedVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.common.Mod;

@Mod("mymod")
public class MyMod {
    public void run() {
        DetectedVersion.tryDetectVersion();
    }

    public static void doStuff() {
        ServerLevel.END_SPAWN_POINT = new BlockPos(1, 2, 3);
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
