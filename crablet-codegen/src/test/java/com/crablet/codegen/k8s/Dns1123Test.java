package com.crablet.codegen.k8s;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Dns1123Test {

    @Test
    void lowercasesAndReplacesSpaceWithHyphen() {
        assertThat(Dns1123.sanitize("My Great App"))
                .isEqualTo("my-great-app");
    }

    @Test
    void collapsesConsecutiveNonAlphanum() {
        assertThat(Dns1123.sanitize("a__b..c---d"))
                .isEqualTo("a-b-c-d");
    }

    @Test
    void trimsEdgeHyphens() {
        assertThat(Dns1123.sanitize("---x---"))
                .isEqualTo("x");
    }

    @Test
    void truncatesTo63() {
        String s = "a".repeat(80);
        assertThat(Dns1123.sanitize(s).length()).isLessThanOrEqualTo(63);
    }

    @Test
    void emptyFallsBackToApp() {
        assertThat(Dns1123.sanitize(""))
                .isEqualTo("app");
        assertThat(Dns1123.sanitize("   "))
                .isEqualTo("app");
        assertThat(Dns1123.sanitize("___"))
                .isEqualTo("app");
    }
}
