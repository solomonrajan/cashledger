package com.oriondev.moneywallet.updater

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class UpdaterActivityTest {
    @Test
    fun testLaunchUpdaterActivity() {
        ActivityScenario.launch(UpdaterActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Do nothing, just ensure it doesn't crash on launch
            }
        }
    }
}
