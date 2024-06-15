package coremod;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import net.neoforged.neoforgespi.coremod.ICoreMod;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Set;

public class CoreMod implements ICoreMod {
    @Override
    public Iterable<? extends cpw.mods.modlauncher.api.ITransformer<?>> getTransformers() {
        return List.of(
                new ITransformer<ClassNode>() {
                    @Override
                    public ClassNode transform(ClassNode classNode, ITransformerVotingContext context) {
                        classNode.visitField(
                                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                                "CORE_MOD_MARKER",
                                "Z",
                                null,
                                true
                        );
                        return classNode;
                    }

                    @Override
                    public TransformerVoteResult castVote(ITransformerVotingContext context) {
                        return TransformerVoteResult.YES;
                    }

                    @Override
                    public Set<Target<ClassNode>> targets() {
                        return Set.of(Target.targetClass("net.minecraft.world.item.ItemStack"));
                    }

                    @Override
                    public TargetType<ClassNode> getTargetType() {
                        return TargetType.CLASS;
                    }
                }
        );
    }
}
