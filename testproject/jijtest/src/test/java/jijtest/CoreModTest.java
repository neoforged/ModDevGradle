package jijtest;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CoreModTest {
    @Test
    void testPresenceOfCoreMod() throws Exception {
        var field = ItemStack.class.getField("CORE_MOD_MARKER");
        assertEquals(true, field.get(null));
    }
}
