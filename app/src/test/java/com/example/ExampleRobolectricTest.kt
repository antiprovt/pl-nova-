package com.example

import android.content.Context
import androidx.compose.ui.test.*
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.ShiftRepository
import com.example.ui.ShiftViewModel
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Šichtér", appName)
  }

  @Test
  fun testRenderShiftAppScreen() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val repository = ShiftRepository(db.shiftDao())
    val viewModel = ShiftViewModel(repository)

    composeTestRule.setContent {
        ShiftAppScreen(viewModel = viewModel)
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun testUserInteractionFlows() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val repository = ShiftRepository(db.shiftDao())
    val viewModel = ShiftViewModel(repository)

    composeTestRule.setContent {
        ShiftAppScreen(viewModel = viewModel)
    }
    composeTestRule.waitForIdle()

    // 1. Verify next and previous month navigation does not crash
    composeTestRule.onNodeWithTag("next_month_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("prev_month_btn").performClick()
    composeTestRule.waitForIdle()

    // 2. Click a calendar day (e.g., day 15)
    composeTestRule.onNodeWithTag("day_15").performClick()
    composeTestRule.waitForIdle()

    // 3. Open settings, switch theme options, toggle switch preferences, then close
    composeTestRule.onNodeWithTag("settings_btn").performClick()
    composeTestRule.waitForIdle()
    
    composeTestRule.onNodeWithTag("settings_dialog").assertIsDisplayed()
    
    // Toggle countdown setting switch
    composeTestRule.onNodeWithTag("settings_countdown_switch").performClick()
    composeTestRule.waitForIdle()
    
    // Toggle cleaner mode switch
    composeTestRule.onNodeWithTag("settings_cleaner_switch").performClick()
    composeTestRule.waitForIdle()

    // Expand financial settings
    composeTestRule.onNodeWithTag("settings_financial_toggle_card").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_tariff_salary_input").performScrollTo().assertIsDisplayed()

    // Collapse financial settings
    composeTestRule.onNodeWithTag("settings_financial_toggle_card").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Click close setting button
    composeTestRule.onNodeWithTag("settings_close_btn").performClick()
    composeTestRule.waitForIdle()

    // 4. Switch tabs to stats
    composeTestRule.onNodeWithTag("tab_stats").performClick()
    composeTestRule.waitForIdle()

    // Switch back to edit tab
    composeTestRule.onNodeWithTag("tab_edit_day").performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun testLaunchMainActivity() {
    androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assert(activity != null)
      }
    }
  }
}
