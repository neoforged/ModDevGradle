package mymod;

import net.minecraft.DetectedVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.fml.common.Mod;

@Mod("mymod")
public class MyMod {
    public void run() {
        DetectedVersion.tryDetectVersion();
    }

    public static void doStuff() {
        // Test our AT
        ServerLevel.END_SPAWN_POINT = new BlockPos(1, 2, 3);
        // Test a Forge AT (in a class that is not binpatched)
        var block = new EnderChestBlock(BlockBehaviour.Properties.of());
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
