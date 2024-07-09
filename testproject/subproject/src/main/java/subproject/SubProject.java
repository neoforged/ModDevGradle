package subproject;

import net.minecraft.DetectedVersion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.common.Mod;

@Mod("testproject")
public class SubProject {
    public SubProject() {
        System.out.println("Subproject: " + ((DetectedVersion) DetectedVersion.BUILT_IN).buildTime);

        System.out.println(new ItemStack(Items.ACACIA_BOAT).testmodThisIsMine());
    }
}
