package org.remus.giteabot.prworkflow.unittest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitTestCaseRepository extends JpaRepository<UnitTestCase, Long> {

    /** Finds the case for the given path inside the given suite (upsert decision). */
    Optional<UnitTestCase> findBySuiteAndPath(UnitTestSuite suite, String path);

    /** All cases of the suite, in id order. */
    List<UnitTestCase> findBySuiteOrderByIdAsc(UnitTestSuite suite);
}

