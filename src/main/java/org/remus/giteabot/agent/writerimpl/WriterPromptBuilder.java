package org.remus.giteabot.agent.writerimpl;

import org.remus.giteabot.agent.model.ImplementationPlan;
import org.remus.giteabot.agent.validation.ToolResult;

import java.util.List;

public class WriterPromptBuilder {

    public String buildInitialPrompt(Long issueNumber, String issueTitle, String issueBody, String treeContext) {
        return String.format("""
                ## Originating issue
                Number: #%d
                Title: %s
                
                Body:
                %s
                
                ## Repository files
                %s
                
                Improve this issue or ask the minimum critical follow-up questions.
                """, issueNumber, issueTitle, issueBody != null ? issueBody : "(empty)", treeContext);
    }

    public String buildContinuationPrompt(String commentBody) {
        return "The issue author answered:\n\n" + (commentBody != null ? commentBody : "");
    }

    public String buildToolFeedback(List<ImplementationPlan.ToolRequest> requests, List<ToolResult> results) {
        StringBuilder sb = new StringBuilder("## Writer tool results\n\n");
        for (int i = 0; i < requests.size(); i++) {
            ImplementationPlan.ToolRequest request = requests.get(i);
            ToolResult result = results.get(i);
            sb.append("### Result for `").append(request.getId()).append("`: `")
                    .append(request.getTool()).append("`\n\n");
            if (result.success()) {
                sb.append(result.output() == null || result.output().isBlank() ? "(no output)" : result.output());
            } else {
                sb.append("Failed: ").append(result.error() == null ? result.output() : result.error());
            }
            sb.append("\n\n");
        }
        sb.append("Use these results to continue. If no critical questions remain, return the final revised issue draft.");
        return sb.toString();
    }

    public String buildIssueBody(Long originatingIssueNumber, WriterPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("Originates from #").append(originatingIssueNumber).append("\n\n");
        if (plan.getQualityAssessment() != null && !plan.getQualityAssessment().isBlank()) {
            sb.append("## Quality assessment\n\n").append(plan.getQualityAssessment()).append("\n\n");
        }
        sb.append(plan.getRevisedIssueDraft() != null ? plan.getRevisedIssueDraft() : "");
        appendList(sb, "Assumptions", plan.getAssumptions());
        appendList(sb, "Open questions", plan.getOpenQuestions());
        sb.append("\n\n---\n*This issue was automatically drafted by the AI technical-writer agent.*\n");
        return sb.toString();
    }

    public String buildClarifyingQuestionComment(WriterPlan plan) {
        StringBuilder sb = new StringBuilder("🤖 **AI Technical Writer**\n\n");
        boolean hasAssessment = plan.getQualityAssessment() != null && !plan.getQualityAssessment().isBlank();
        if (hasAssessment) {
            sb.append("**Quality assessment:** ").append(plan.getQualityAssessment()).append("\n\n");
        }
        List<String> questions = plan.getClarifyingQuestions();
        if (questions != null && !questions.isEmpty()) {
            sb.append("I need the issue author to answer these questions before I can create the improved issue:\n\n");
            for (String question : questions) {
                if (question == null || question.isBlank()) {
                    continue;
                }
                sb.append("- ").append(question).append("\n");
            }
        } else if (!hasAssessment) {
            // Neither structured questions nor free-form assessment: ask the
            // author generically for more context so the comment is never empty.
            sb.append("I do not yet have enough information to draft an improved issue. ")
                    .append("Could you please add more context (acceptance criteria, intended user, ")
                    .append("affected components, examples) and mention me again?\n");
        }
        // If we have an assessment but no structured questions, the assessment
        // itself typically already lists the open points — don't append a second
        // boilerplate paragraph that would duplicate or contradict it.
        return sb.toString();
    }

    private void appendList(StringBuilder sb, String title, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        sb.append("\n\n## ").append(title).append("\n\n");
        for (String value : values) {
            sb.append("- ").append(value).append("\n");
        }
    }

    public String buildTreeContext(List<java.util.Map<String, Object>> tree, int maxFiles) {
        if (tree == null || tree.isEmpty()) {
            return "No repository tree is available.";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (java.util.Map<String, Object> entry : tree) {
            if (count >= maxFiles) {
                sb.append("... (truncated, ").append(tree.size() - count).append(" more entries)\n");
                break;
            }
            String type = String.valueOf(entry.getOrDefault("type", "blob"));
            String path = String.valueOf(entry.getOrDefault("path", ""));
            if ("blob".equals(type) && !path.isBlank()) {
                sb.append("- ").append(path).append("\n");
                count++;
            }
        }
        return sb.isEmpty() ? "No repository files found." : sb.toString();
    }
}
