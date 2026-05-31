package com.crablet.codegen.k8s;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ViewNameResolverTest {

    @Test
    void pascalToKebab() {
        assertThat(ViewNameResolver.viewName("WalletBalanceView")).isEqualTo("wallet-balance-view");
    }
}
