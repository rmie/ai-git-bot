package org.remus.giteabot.prworkflow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CRUD UI for {@link WorkflowConfiguration} rows. Mirrors the structure of
 * the MCP / Bot-tool configuration controllers (list-on-System-settings,
 * dedicated form + sub-page for the workflow selection).
 */
@Slf4j
@Controller
@RequestMapping("/system-settings/workflow-configurations")
public class WorkflowConfigurationController {

    private final WorkflowConfigurationService configurationService;
    private final WorkflowSelectionService selectionService;

    public WorkflowConfigurationController(WorkflowConfigurationService configurationService,
                                           WorkflowSelectionService selectionService) {
        this.configurationService = configurationService;
        this.selectionService = selectionService;
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("workflowConfiguration", new WorkflowConfiguration());
        model.addAttribute("activeNav", "system-settings");
        return "system-settings/workflow-configurations/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return configurationService.findById(id)
                .map(configuration -> {
                    model.addAttribute("workflowConfiguration", configuration);
                    model.addAttribute("activeNav", "system-settings");
                    return "system-settings/workflow-configurations/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Workflow configuration not found");
                    return "redirect:/system-settings";
                });
    }

    @GetMapping("/{id}/clone")
    public String cloneForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            WorkflowConfiguration clone = configurationService.cloneConfiguration(id);
            model.addAttribute("workflowConfiguration", clone);
            model.addAttribute("activeNav", "system-settings");
            return "system-settings/workflow-configurations/form";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/system-settings";
        }
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("workflowConfiguration") WorkflowConfiguration workflowConfiguration,
                       Model model, RedirectAttributes redirectAttributes) {
        try {
            WorkflowConfiguration saved = configurationService.save(workflowConfiguration);
            redirectAttributes.addFlashAttribute("success",
                    "Workflow configuration saved. Please select which workflows are enabled.");
            return "redirect:/system-settings/workflow-configurations/" + saved.getId() + "/workflows";
        } catch (Exception e) {
            log.error("Failed to save workflow configuration", e);
            model.addAttribute("error", "Failed to save: " + e.getMessage());
            model.addAttribute("workflowConfiguration", workflowConfiguration);
            model.addAttribute("activeNav", "system-settings");
            return "system-settings/workflow-configurations/form";
        }
    }

    @GetMapping("/{id}/workflows")
    public String workflowSelection(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return configurationService.findById(id)
                .map(configuration -> {
                    List<WorkflowSelectionRow> rows = selectionService.loadAvailableWorkflows(id);
                    model.addAttribute("workflowConfiguration", configuration);
                    model.addAttribute("workflows", rows);
                    model.addAttribute("activeNav", "system-settings");
                    return "system-settings/workflow-configurations/workflows";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Workflow configuration not found");
                    return "redirect:/system-settings";
                });
    }

    @PostMapping("/{id}/workflows/save")
    public String saveWorkflowSelection(@PathVariable Long id,
                                        @RequestParam(name = "selectedWorkflowKeys", required = false)
                                        List<String> selectedWorkflowKeys,
                                        @RequestParam Map<String, String> allParams,
                                        RedirectAttributes redirectAttributes) {
        try {
            Map<String, String> workflowParams = extractWorkflowParams(allParams, selectedWorkflowKeys);
            selectionService.saveSelection(id, selectedWorkflowKeys, workflowParams);
            redirectAttributes.addFlashAttribute("success", "Workflow selection saved successfully");
            return "redirect:/system-settings";
        } catch (Exception e) {
            log.error("Failed to save workflow selection", e);
            redirectAttributes.addFlashAttribute("error", "Failed to save workflow selection: " + e.getMessage());
            return "redirect:/system-settings/workflow-configurations/" + id + "/workflows";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            configurationService.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Workflow configuration deleted successfully");
        } catch (Exception e) {
            log.error("Failed to delete workflow configuration", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete: " + e.getMessage());
        }
        return "redirect:/system-settings";
    }

    /**
     * Returns the selected workflows for the Bot Details modal (mirrors the
     * MCP / built-in tool endpoints under {@code /bots/...}).
     */
    @GetMapping("/{id}/selected-workflows")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> selectedWorkflows(@PathVariable Long id) {
        if (configurationService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> rows = selectionService.describeSelections(id).stream()
                .map(row -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("workflowKey", row.workflowKey());
                    out.put("displayName", row.displayName());
                    out.put("category", row.category());
                    out.put("params", selectionService.maskSecrets(row.workflowKey(), row.paramsJson()));
                    return out;
                })
                .toList();
        return ResponseEntity.ok(rows);
    }

    /**
     * Extracts the {@code params__<workflowKey>__<fieldName>} request
     * parameters submitted by the workflow-selection form into a
     * {@code workflowKey -> {fieldName -> value}} map serialised as a per
     * workflow JSON snippet ({@code "{\"field\":\"value\",...}"}) — the
     * {@link WorkflowSelectionService} validates them against each
     * workflow's {@link org.remus.giteabot.prworkflow.WorkflowParamsSchema}.
     */
    private Map<String, String> extractWorkflowParams(Map<String, String> allParams,
                                                     List<String> selectedKeys) {
        Map<String, Map<String, String>> grouped = new LinkedHashMap<>();
        if (allParams != null) {
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith("params__")) {
                    continue;
                }
                String rest = key.substring("params__".length());
                int sep = rest.indexOf("__");
                if (sep < 0) {
                    continue;
                }
                String workflowKey = rest.substring(0, sep);
                String fieldName = rest.substring(sep + 2);
                grouped.computeIfAbsent(workflowKey, k -> new LinkedHashMap<>())
                        .put(fieldName, entry.getValue());
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : grouped.entrySet()) {
            if (selectedKeys != null && !selectedKeys.contains(entry.getKey())) {
                continue;
            }
            result.put(entry.getKey(), toJson(entry.getValue()));
        }
        return result;
    }

    private String toJson(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append('"').append(':')
                    .append('"').append(escape(e.getValue() == null ? "" : e.getValue())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}

