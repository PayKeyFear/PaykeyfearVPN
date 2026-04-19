package com.paykeyfear.vpn

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test: the app launches, home screen renders the brand title, and
 * Material icons for navigation destinations are present.
 *
 * Keep this fast — it exists only to catch broken wiring (missing theme
 * provider, Hilt module failing, etc.). Deeper UI flows live in per-screen
 * tests as we grow them.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ImportScreenSmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_launches_and_renders_home_title() {
        compose.onNodeWithText("PaykeyfearVPN").assertIsDisplayed()
    }

    @Test
    fun bottom_nav_exposes_all_destinations() {
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithText("Servers").assertIsDisplayed()
        compose.onNodeWithText("Import").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }
}
