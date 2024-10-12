package legacytest;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.DetectedVersion;
import net.minecraft.client.main.Main;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class LegacyTest {
    public static void main(String[] args) {
        System.out.println(DetectedVersion.tryDetectVersion());
        Main.main(new String[]{

        });

        System.out.println(new ItemStack(Items.ACACIA_LEAVES).getCount());
        RenderSystem.disableBlend();
    }
}
