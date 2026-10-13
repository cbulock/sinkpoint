package com.cbulock.sinkpoint

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Uses a dedicated QA install; does not clear app data. Physical Nearby testing is separate. */
class DeviceSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun homeSettingsAndTutorialAreReachable() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("new-multiplayer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Appearance").assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Learn to play").performScrollTo().performClick()
        compose.onNodeWithText("Hide your fleet").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithContentDescription("B3, untried").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.onNodeWithText("Fire").performScrollTo().performClick()
        compose.onNodeWithText("Hit! Try the cells beside it in a real match.").assertExists()
    }
}
