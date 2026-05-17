package org.remus.giteabot.agent.shared;

import org.remus.giteabot.agent.validation.ToolResult;

/**
 * Branch reference utilities shared by all agent implementations.
 * <p>
 * Centralises the normalisation of Git refs and the parsing of the
 * {@code branch-switcher} tool output so that coding and writer agents
 * stay byte-identical.
 */
public final class BranchRefs {

    /** Marker the {@code branch-switcher} tool emits to announce a successful switch. */
    public static final String BRANCH_SWITCH_PREFIX = "Switched workspace branch to:";

    private BranchRefs() {
    }

    /**
     * Strips the {@code refs/heads/} or {@code refs/tags/} prefix from a Git ref.
     *
     * @return the bare branch name, or {@code null} if {@code ref} is blank.
     */
    public static String normalize(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        if (ref.startsWith("refs/heads/")) {
            return ref.substring("refs/heads/".length());
        }
        if (ref.startsWith("refs/tags/")) {
            return ref.substring("refs/tags/".length());
        }
        return ref;
    }

    /**
     * Extracts the branch name announced by a successful {@code branch-switcher} tool result.
     *
     * @return the new branch name, or {@code null} if the tool failed or did not announce one.
     */
    public static String extractSwitchedBranch(ToolResult result) {
        if (result == null || !result.success()) {
            return null;
        }
        String output = result.output();
        if (output == null || output.isBlank()) {
            return null;
        }
        int idx = output.indexOf(BRANCH_SWITCH_PREFIX);
        if (idx < 0) {
            return null;
        }
        return normalize(output.substring(idx + BRANCH_SWITCH_PREFIX.length()).trim());
    }
}

