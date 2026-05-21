package org.remus.giteabot.prworkflow.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowSelectionRepository extends JpaRepository<WorkflowSelection, Long> {

    List<WorkflowSelection> findByConfigurationIdOrderByWorkflowKeyAsc(Long configurationId);

    List<WorkflowSelection> findByConfigurationId(Long configurationId);

    Optional<WorkflowSelection> findByConfigurationIdAndWorkflowKey(Long configurationId, String workflowKey);

    void deleteByConfigurationId(Long configurationId);

    void deleteByConfigurationIdAndWorkflowKey(Long configurationId, String workflowKey);
}

