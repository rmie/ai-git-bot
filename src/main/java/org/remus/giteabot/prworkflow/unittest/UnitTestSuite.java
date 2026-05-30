package org.remus.giteabot.prworkflow.unittest;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.remus.giteabot.prworkflow.e2e.SuiteLifecycleMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One generated unit-test suite tied to a single
 * {@link org.remus.giteabot.prworkflow.PrWorkflowRun}. Created by
 * {@code UnitTestWorkflow} for one PR, then populated with
 * {@link UnitTestCase} rows by the author agent and executed by the project's
 * own test runner.
 *
 * <p>Mirrors {@code PrTestSuite} but for white-box unit tests committed onto
 * the PR branch (no preview environment). The {@link SuiteLifecycleMode} enum
 * is shared with the E2E workflow because it is framework-agnostic.</p>
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "unit_test_suites")
public class UnitTestSuite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "pr_number", nullable = false)
    private Long prNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UnitTestFramework framework;

    /** Git ref the author inspected, captured for reproducibility. */
    @Column(name = "source_tree_ref", length = 255)
    private String sourceTreeRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_mode", nullable = false, length = 32)
    private SuiteLifecycleMode lifecycleMode = SuiteLifecycleMode.COMMIT_TO_PR;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "suite", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("path ASC")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<UnitTestCase> cases = new ArrayList<>();

    @PrePersist
    void onPrePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (lifecycleMode == null) {
            lifecycleMode = SuiteLifecycleMode.COMMIT_TO_PR;
        }
    }

    public void addCase(UnitTestCase testCase) {
        testCase.setSuite(this);
        cases.add(testCase);
    }
}

