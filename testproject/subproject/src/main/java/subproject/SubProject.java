package subproject;

import net.minecraft.DetectedVersion;
import net.neoforged.fml.common.Mod;

@Mod("testproject")
public class SubProject {
    public SubProject() {
        System.out.println("Subproject: " + ((DetectedVersion) DetectedVersion.BUILT_IN).buildTime);
    }
}
