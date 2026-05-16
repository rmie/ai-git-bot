package org.remus.giteabot.agent.loop;

import lombok.Setter;
import org.remus.giteabot.agent.session.AgentSession;

import java.nio.file.Path;

/**
 * Mutable, per-run context shared between {@link AgentLoop} and
 * {@link AgentStrategy} implementations.
 *
 * <p>{@link #baseBranch()} is mutable because strategies may switch the
 * checked-out branch via {@code branch-switcher} mid-run; the resulting branch
 * is reported back to the orchestrating service through this object.</p>
 */
public final class AgentRunContext {

    private final AgentSession session;
    private final String owner;
    private final String repo;
    private final Long issueNumber;
    private final Path workspaceDir;
    @Setter
    private String baseBranch;

    public AgentRunContext(AgentSession session, String owner, String repo,
                           Long issueNumber, Path workspaceDir, String baseBranch) {
        this.session = session;
        this.owner = owner;
        this.repo = repo;
        this.issueNumber = issueNumber;
        this.workspaceDir = workspaceDir;
        this.baseBranch = baseBranch;
    }

    public AgentSession session() { return session; }
    public String owner() { return owner; }
    public String repo() { return repo; }
    public Long issueNumber() { return issueNumber; }
    public Path workspaceDir() { return workspaceDir; }
    public String baseBranch() { return baseBranch; }
}

