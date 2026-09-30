package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Game Turbo", appName)
  }

  @Test
  fun `verify semantic version parser and comparison`() {
    val v100 = com.example.engine.update.InAppUpdateManager.parseSemVer("1.0.0")
    val v101 = com.example.engine.update.InAppUpdateManager.parseSemVer("v1.0.1")
    val vV200 = com.example.engine.update.InAppUpdateManager.parseSemVer("V2.0.0")
    val invalidTag = com.example.engine.update.InAppUpdateManager.parseSemVer("debug-apk-build-3-1")

    org.junit.Assert.assertNotNull(v100)
    org.junit.Assert.assertNotNull(v101)
    org.junit.Assert.assertNotNull(vV200)
    org.junit.Assert.assertNull("Invalid build tags must be rejected", invalidTag)

    org.junit.Assert.assertTrue("1.0.1 must be greater than 1.0.0", v101!! > v100!!)
    org.junit.Assert.assertFalse("Same version must NOT be greater", v100 > v100)
    org.junit.Assert.assertTrue("2.0.0 must be greater than 1.0.1", vV200!! > v101)
  }

  @Test
  fun `verify GitHub repository constants`() {
    assertEquals("Eslam3537", com.example.engine.update.InAppUpdateManager.GITHUB_OWNER)
    assertEquals("Game-Turbo", com.example.engine.update.InAppUpdateManager.GITHUB_REPO)
  }
}
