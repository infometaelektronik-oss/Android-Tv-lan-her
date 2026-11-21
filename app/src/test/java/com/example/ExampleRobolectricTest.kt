package com.example

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.example.data.LauncherPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SadeTV", appName)
  }

  @Test
  fun `default card order places youtube tv and tgdizifilm first`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = LauncherPrefs(context)
    val order = prefs.cardOrderIds
    assertEquals("youtube", order[0])
    assertEquals("television", order[1])
    assertEquals("tgdizifilm", order[2])
    assertEquals("all_apps", order.last())
  }

  @Test
  fun `main activity launches and binds recycler views`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    val rvMain = activity.findViewById<RecyclerView>(R.id.rvMainCards)
    val clock = activity.findViewById<View>(R.id.tvClock)
    val gear = activity.findViewById<View>(R.id.btnSettings)
    assertNotNull(rvMain)
    assertNotNull(clock)
    assertNotNull(gear)
    assertTrue((rvMain.adapter?.itemCount ?: 0) >= 5)
  }
}
