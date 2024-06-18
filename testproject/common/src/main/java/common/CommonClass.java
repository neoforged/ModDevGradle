package common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class CommonClass {
    public static void doStuff() {
        ServerLevel.END_SPAWN_POINT = new BlockPos(1, 2, 3);
    }
}
