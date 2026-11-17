package com.oriondev.moneywallet.ui.activity.base

import android.content.Intent
import com.oriondev.moneywallet.model.LockMode
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.LockActivity

abstract class BaseActivity : ThemedActivity() {

    private var activityLocked = true
    private var activityLockEnabled = true
    private var activityResultOk = true

    override fun onResume() {
        super.onResume()
        if (PreferenceManager.getCurrentLockMode() != LockMode.NONE) {
            if (System.currentTimeMillis() - PreferenceManager.getLastLockTime() > MAX_LOCK_TIME_INTERVAL) {
                activityLocked = true
                if (activityResultOk) {
                    val intent = Intent(this, LockActivity::class.java)
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQUEST_CODE_LOCK_ACTIVITY)
                } else {
                    activityResultOk = true
                }
            } else {
                activityLocked = false
            }
            activityLockEnabled = true
        } else {
            activityLocked = false
            activityLockEnabled = false
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_LOCK_ACTIVITY) {
            if (resultCode != RESULT_OK) {
                moveTaskToBack(true)
                activityResultOk = false
            } else {
                activityResultOk = true
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onPause() {
        super.onPause()
        if (activityLockEnabled && !activityLocked) {
            PreferenceManager.setLastLockTime(System.currentTimeMillis())
        }
    }

    companion object {
        private const val REQUEST_CODE_LOCK_ACTIVITY = 20
        private const val MAX_LOCK_TIME_INTERVAL = 1000L
    }
}
