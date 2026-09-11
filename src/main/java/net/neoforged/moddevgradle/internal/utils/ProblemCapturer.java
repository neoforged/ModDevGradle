package net.neoforged.moddevgradle.internal.utils;

import javax.inject.Inject;
import org.gradle.api.problems.Problems;

/**
 * Sometimes, you've got problems, but all you have is a Project
 */
public abstract class ProblemCapturer {
    @Inject
    public abstract Problems getProblems();
}
