package net.neoforged.moddevgradle.internal.utils;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.problems.Problem;
import org.gradle.api.Action;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.Severity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Our lowest supported Gradle version is 8.8. The Problems Api has significantly changed
 * since then. This class aims to hide the details of supporting this transparently.
 * <p>
 * The {@link Problems} interface has been added in Gradle 8.6 and is thus safe to use in
 * the public interface of this class.
 * <p>
 * {@link ProblemId} and {@link ProblemGroup} are also fine to use, since they're available since Gradle 8.8.
 */
public final class ProblemReportingUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ProblemReportingUtil.class);

    private static final Map<net.neoforged.problems.ProblemGroup, ProblemGroup> PROBLEM_GROUPS = new ConcurrentHashMap<>();
    private static final Map<net.neoforged.problems.ProblemId, ProblemId> PROBLEM_IDS = new ConcurrentHashMap<>();

    private ProblemReportingUtil() {}

    public static void report(Problems problems, Problem problem) {
        try {
            var id = getProblemId(problem.problemId());
            Gradle813Adapter.report(problems, id, spec -> applyProblemToProblemSpec(problem, spec));
            return;
        } catch (ProblemsApiUnsupported ignored) {}

        // As a fallback, report on the console
        switch (problem.severity()) {
            case ADVICE -> LOG.info("{}", problem);
            case WARNING -> LOG.warn("{}", problem);
            case ERROR -> LOG.error("{}", problem);
        }
    }

    private static void applyProblemToProblemSpec(Problem problem, ProblemSpec spec) {
        switch (problem.severity()) {
            case ADVICE -> spec.severity(Severity.ADVICE);
            case WARNING -> spec.severity(Severity.WARNING);
            case ERROR -> spec.severity(Severity.ERROR);
        }

        if (problem.contextualLabel() != null) {
            spec.contextualLabel(problem.contextualLabel());
        }
        if (problem.details() != null) {
            spec.details(problem.details());
        }
        if (problem.solution() != null) {
            spec.solution(problem.solution());
        }
        if (problem.documentedAt() != null) {
            spec.documentedAt(problem.documentedAt());
        }

        var location = problem.location();
        if (location != null) {
            var filePath = location.file().toAbsolutePath().toString();
            if (location.offset() != null) {
                int length = Objects.requireNonNullElse(location.length(), 0);
                spec.offsetInFileLocation(filePath, location.offset(), length);
            } else if (location.line() != null) {
                if (location.column() != null) {
                    if (location.length() != null) {
                        spec.lineInFileLocation(filePath, location.line(), location.column(), location.length());
                    } else {
                        spec.lineInFileLocation(filePath, location.line(), location.column());
                    }
                } else {
                    spec.lineInFileLocation(filePath, location.line());
                }
            } else {
                spec.fileLocation(filePath);
            }
        }
    }

    private static ProblemId getProblemId(net.neoforged.problems.ProblemId problemId) {
        return PROBLEM_IDS.computeIfAbsent(problemId, ProblemReportingUtil::convertProblemId);
    }

    private static ProblemId convertProblemId(net.neoforged.problems.ProblemId problemId) {
        return Gradle813Adapter.createProblemId(
                problemId.id(),
                problemId.displayName(),
                getProblemGroup(problemId.group()));
    }

    private static ProblemGroup getProblemGroup(net.neoforged.problems.ProblemGroup problemGroup) {
        return PROBLEM_GROUPS.computeIfAbsent(problemGroup, ProblemReportingUtil::convertProblemGroup);
    }

    private static ProblemGroup convertProblemGroup(net.neoforged.problems.ProblemGroup problemGroup) {
        return Gradle813Adapter.createProblemGroup(
                problemGroup.id(),
                problemGroup.displayName(),
                problemGroup.parent() == null ? null : getProblemGroup(problemGroup.parent()));
    }

    /**
     * We're compiling against Gradle 8.8, but these methods have become available only in 8.13.
     */
    private static class Gradle813Adapter {
        public static ProblemId createProblemId(String id, String displayName, ProblemGroup problemGroup) {
            try {
                var factory = ProblemId.class.getMethod("create", String.class, String.class, ProblemGroup.class);
                return (ProblemId) factory.invoke(null, id, displayName, problemGroup);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                LOG.debug("Problems API is unsupported.", e);
                throw new ProblemsApiUnsupported();
            }
        }

        public static ProblemGroup createProblemGroup(String id, String displayName, @Nullable ProblemGroup parent) {
            try {
                if (parent == null) {
                    var factory = ProblemGroup.class.getMethod("create", String.class, String.class);
                    return (ProblemGroup) factory.invoke(null, id, displayName);
                } else {
                    var factory = ProblemGroup.class.getMethod("create", String.class, String.class, ProblemGroup.class);
                    return (ProblemGroup) factory.invoke(null, id, displayName, parent);
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                LOG.debug("Problems API is unsupported.", e);
                throw new ProblemsApiUnsupported();
            }
        }

        public static void report(Problems problems, ProblemId id, Action<ProblemSpec> consumer) {
            try {
                var reporter = problems.getClass().getMethod("getReporter").invoke(problems);

                var problemFactory = reporter.getClass().getMethod("create", ProblemId.class, Action.class);
                var problem = problemFactory.invoke(reporter, id, consumer);

                var problemClass = Class.forName("org.gradle.api.problems.Problem", true, reporter.getClass().getClassLoader());

                var problemConsumer = reporter.getClass().getMethod("report", problemClass);
                problemConsumer.invoke(reporter, problem);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
                LOG.debug("Problems API is unsupported.", e);
                throw new ProblemsApiUnsupported();
            }
        }
    }

    private static class ProblemsApiUnsupported extends RuntimeException {}
}
