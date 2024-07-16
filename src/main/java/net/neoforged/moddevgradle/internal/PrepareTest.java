package net.neoforged.moddevgradle.internal;

import org.gradle.api.GradleException;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;

abstract class PrepareTest extends PrepareRunOrTest {
    @Inject
    public PrepareTest() {
        super(ProgramArgsFormat.FML_JUNIT);
    }

    @Override
    protected UserDevRunType resolveRunType(final UserDevConfig userDevConfig) {
        final UserDevRunType runConfig = userDevConfig.runs().get("junit");
        if (runConfig == null) {
            throw new GradleException("The unit testing plugin requires a 'junit' run-type to be made available by NeoForge. Available run types: " + userDevConfig.runs().keySet());
        }
        return runConfig;
    }

    @Override
    @Nullable
    protected String resolveMainClass(final UserDevRunType runConfig) {
        return null; // No main class to override
    }

    @Override
    protected boolean isClientDistribution() {
        return true;
    }
}
