package coremod;

import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import net.neoforged.neoforgespi.transformation.SimpleClassProcessor;
import net.neoforged.neoforgespi.transformation.SimpleTransformationContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.Set;

public class CoreMod implements ClassProcessorProvider {
    @Override
    public void createProcessors(Context context, Collector collector) {
        collector.add(new SimpleClassProcessor() {
            @Override
            public void transform(ClassNode input, SimpleTransformationContext context) {
                input.visitField(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "CORE_MOD_MARKER",
                        "Z",
                        null,
                        true
                );
            }

            @Override
            public Set<Target> targets() {
                return Set.of(new Target("net.minecraft.world.item.ItemStack"));
            }

            @Override
            public ProcessorName name() {
                return ProcessorName.parse("mdg:coremodtest");
            }
        });
    }
}
