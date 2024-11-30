package mymod;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

@JeiPlugin
public class JeiCompat implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation("mymod:mymod");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // Calling this method tests that JEI was remapped correctly, since the method has an "ItemStack" argument
        registration.registerSubtypeInterpreter(Items.ALLIUM, (ingredient, context) -> "allium");
    }
}
