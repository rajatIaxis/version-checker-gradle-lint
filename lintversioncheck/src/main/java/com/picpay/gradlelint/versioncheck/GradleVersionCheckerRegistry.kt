package com.picpay.gradlelint.versioncheck

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.Issue
import com.picpay.gradlelint.versioncheck.GradleVersionChecker.Companion.REMOTE_VERSION

@Suppress("UnstableApiUsage")
class GradleVersionCheckerRegistry: IssueRegistry() {
    override val issues: List<Issue>
        get() = listOf(REMOTE_VERSION)

    override val api: Int
        get() = com.android.tools.lint.detector.api.CURRENT_API

}
