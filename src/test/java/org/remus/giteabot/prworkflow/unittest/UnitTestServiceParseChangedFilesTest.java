package org.remus.giteabot.prworkflow.unittest;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UnitTestServiceParseChangedFilesTest {

    @Test
    void extractsPostImagePathsAndStripsBPrefix() {
        String diff = """
                diff --git a/src/main/java/Foo.java b/src/main/java/Foo.java
                index 111..222 100644
                --- a/src/main/java/Foo.java
                +++ b/src/main/java/Foo.java
                @@ -1 +1 @@
                -old
                +new
                diff --git a/README.md b/README.md
                --- a/README.md
                +++ b/README.md
                @@ -1 +1 @@
                +doc
                """;
        Set<String> files = UnitTestService.parseChangedFiles(diff);
        assertThat(files).containsExactly("src/main/java/Foo.java", "README.md");
    }

    @Test
    void skipsDeletedFiles() {
        String diff = """
                diff --git a/Gone.java b/Gone.java
                --- a/Gone.java
                +++ /dev/null
                """;
        assertThat(UnitTestService.parseChangedFiles(diff)).isEmpty();
    }

    @Test
    void handlesNullDiff() {
        assertThat(UnitTestService.parseChangedFiles(null)).isEmpty();
    }
}

