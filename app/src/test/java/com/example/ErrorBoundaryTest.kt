package com.example

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.error.ErrorBoundary
import com.example.ui.error.LocalErrorBoundary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ErrorBoundaryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun errorBoundary_rendersNormalContentWhenNoError() {
        composeTestRule.setContent {
            ErrorBoundary {
                Text("Normal Content Active")
            }
        }

        // Verify that the normal content is displayed
        composeTestRule.onNodeWithText("Normal Content Active").assertIsDisplayed()
        // Verify that the recovery screen warning is NOT shown
        composeTestRule.onNodeWithText("Oops, Audio City experienced an interruption").assertDoesNotExist()
    }

    @Test
    fun errorBoundary_rendersRecoveryScreenOnReportedException() {
        composeTestRule.setContent {
            ErrorBoundary {
                val boundary = LocalErrorBoundary.current
                androidx.compose.material3.Button(
                    onClick = {
                        boundary.reportError(RuntimeException("Database connection failed"))
                    },
                    modifier = androidx.compose.ui.Modifier
                ) {
                    Text("Trigger Error")
                }
            }
        }

        // Trigger the error by clicking the button
        composeTestRule.onNodeWithText("Trigger Error").performClick()

        // Verify that the fallback screen is displayed with details
        composeTestRule.onNodeWithText("Oops, Audio City", substring = true).assertExists()
        composeTestRule.onNodeWithText("Database connection failed", substring = true).assertExists()
        composeTestRule.onNodeWithText("Hot Relaunch App", substring = true).assertExists()
    }
}
