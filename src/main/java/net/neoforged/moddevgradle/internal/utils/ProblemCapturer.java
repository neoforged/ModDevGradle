package net.neoforged.moddevgradle.internal.utils;

import org.gradle.api.problems.Problems;

import javax.inject.Inject;

/**
 * Sometimes, you've got problems, but all you have is a Project
 */
public abstract class ProblemCapturer {
    @Inject
    public abstract Problems getProblems();
}
