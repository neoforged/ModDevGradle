package split.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import split.SplitMain;

@Mod(value = "splittest", dist = Dist.CLIENT)
public class ClientMain {

    public ClientMain() {
        Minecraft instance = Minecraft.getInstance();
        System.out.println("from client");
        System.out.println(SplitMain.class);
    }
}
