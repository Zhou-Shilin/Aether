package com.zhousl.aether.ui

import android.app.Application
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ExtensionGestureDrawerTest {
    @get:Rule val compose = createComposeRule()

    private fun showPanel(): Pair<DrawerState, ScrollState> {
        lateinit var drawer: DrawerState
        lateinit var scroll: ScrollState
        compose.setContent {
            val state = rememberDrawerState(DrawerValue.Closed)
            val scrollState = rememberScrollState()
            SideEffect { drawer = state; scroll = scrollState }
            ExtensionGestureDrawer(
                drawerState = state,
                drawerContent = { ModalDrawerSheet { Text("Conversations") } },
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    Box(Modifier.fillMaxWidth().height(200.dp).testTag("panel").extensionWebGestureRegion())
                    Box(Modifier.fillMaxWidth().height(200.dp).testTag("outside"))
                    Box(Modifier.fillMaxWidth().height(1200.dp))
                }
            }
        }
        compose.waitForIdle()
        return drawer to scroll
    }

    @Test
    fun panelSwipeDoesNotOpenDrawerButNextOutsideSwipeDoes() {
        val (drawer, _) = showPanel()
        compose.onNodeWithTag("panel").performTouchInput { swipeRight(durationMillis = 400) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(DrawerValue.Closed, drawer.currentValue) }
        compose.onNodeWithTag("outside").performTouchInput { swipeRight(durationMillis = 400) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(DrawerValue.Open, drawer.currentValue) }
    }

    @Test
    fun cancelledPanelTouchRestoresOutsideDrawerSwipe() {
        val (drawer, _) = showPanel()
        compose.onNodeWithTag("panel").performTouchInput { down(center); cancel() }
        compose.waitForIdle()
        compose.onNodeWithTag("outside").performTouchInput { swipeRight(durationMillis = 400) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(DrawerValue.Open, drawer.currentValue) }
    }

    @Test
    fun panelDoesNotConsumeVerticalConversationScroll() {
        val (drawer, scroll) = showPanel()
        compose.onNodeWithTag("panel").performTouchInput { swipeUp(durationMillis = 400) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(scroll.value > 0)
            assertEquals(DrawerValue.Closed, drawer.currentValue)
        }
    }
}
