package org.jlleitschuh.gradle.ktlint.sample.kotlin

import com.pinterest.ktlint.core.RuleProvider
import com.pinterest.ktlint.core.RuleSetProviderV2

class CustomRuleSetProvider : RuleSetProviderV2(CUSTOM_RULE_SET_ID, NO_ABOUT) {

    override fun getRuleProviders(): Set<RuleProvider> =
        setOf(
            RuleProvider { NoVarRule() }
        )

    companion object {
        const val CUSTOM_RULE_SET_ID = "custom"
    }
}
