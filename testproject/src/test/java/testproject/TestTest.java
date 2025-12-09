package testproject;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
public class TestTest {
    @Test
    public void testIngredient(MinecraftServer server) { // required to load tags
        var items = server.registryAccess().lookupOrThrow(Registries.ITEM);
        Assertions.assertTrue(
                Ingredient.of(items.getOrThrow(ItemTags.AXES)).test(Items.DIAMOND_AXE.getDefaultInstance())
        );
    }
}
