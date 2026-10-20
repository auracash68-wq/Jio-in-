package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_rendersHeaderScanActionAndCleanEmptyState() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Docs Scanner").assertIsDisplayed()
        composeTestRule.onNodeWithText("100% Offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan Document").assertIsDisplayed()
        composeTestRule.onNodeWithText("Import from Gallery").assertIsDisplayed()
        composeTestRule.onNodeWithTag("action_exam_resizer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("action_kb_compressor").assertIsDisplayed()
        composeTestRule.onNodeWithTag("action_ocr_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_empty_state_text"))
        composeTestRule.onNodeWithTag("home_empty_state_text").assertIsDisplayed()
    }
}
