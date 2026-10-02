/*
 * Copyright (c) 2018. MoneyWallet
 * Copyright (c) 2026. solomonrajan/CashLedger
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

@file:Suppress("TooManyFunctions", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "UnusedParameter", "EmptyFunctionBlock")

package com.oriondev.moneywallet.ui.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.andrognito.patternlockview.PatternLockView
import com.andrognito.patternlockview.listener.PatternLockViewListener
import com.andrognito.patternlockview.utils.PatternLockUtils
import com.andrognito.pinlockview.IndicatorDots
import com.andrognito.pinlockview.PinLockListener
import com.andrognito.pinlockview.PinLockView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.LockMode
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.base.ThemedActivity
import com.oriondev.moneywallet.ui.view.theme.ITheme
import com.oriondev.moneywallet.utils.SystemBars

class LockActivity : ThemedActivity() {

    private var action = ACTION_UNLOCK
    private var targetLockMode: LockMode = LockMode.NONE

    private lateinit var pinLayout: ViewGroup
    private lateinit var sequenceLayout: ViewGroup
    private lateinit var fingerprintLayout: ViewGroup

    private lateinit var pinHelpTextView: TextView
    private lateinit var pinLockView: PinLockView

    private lateinit var sequenceHelpTextView: TextView
    private lateinit var patternLockView: PatternLockView

    private lateinit var fingerprintHelpTextView: TextView

    private var newCode: String? = null
    private var currentStep = 0
    private var currentLockMode: LockMode? = null
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private var isResumedState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unpackIntent(intent, savedInstanceState)
        initializeUi(savedInstanceState)
        biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onFingerprintScan(true, 0, null)
                }

                override fun onAuthenticationFailed() {
                    onFingerprintScan(false, 0, null)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFingerprintScan(false, errorCode, errString)
                }
            })
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setNegativeButtonText(getString(android.R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    }

    override fun onResume() {
        super.onResume()
        isResumedState = true
        maybeShowBiometricPrompt()
    }

    override fun onPause() {
        super.onPause()
        isResumedState = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SS_NEW_CODE, newCode)
        outState.putInt(SS_CURRENT_STEP, currentStep)
        outState.putSerializable(SS_CURRENT_LOCK_MODE, currentLockMode)
    }

    private fun unpackIntent(intent: Intent, savedInstanceState: Bundle?) {
        action = intent.getIntExtra(ACTION, ACTION_UNLOCK)
        targetLockMode = (intent.getSerializableExtra(MODE) as? LockMode) ?: LockMode.NONE

        if (savedInstanceState != null) {
            newCode = savedInstanceState.getString(SS_NEW_CODE)
            currentStep = savedInstanceState.getInt(SS_CURRENT_STEP)
            currentLockMode = savedInstanceState.getSerializable(SS_CURRENT_LOCK_MODE) as? LockMode
        } else {
            when (action) {
                ACTION_UNLOCK, ACTION_DISABLE, ACTION_CHANGE_KEY, ACTION_CHANGE_MODE ->
                    currentLockMode = PreferenceManager.getCurrentLockMode()
                ACTION_ENABLE ->
                    currentLockMode = targetLockMode
            }
        }
    }

    override fun getColorBehindStatusBar(theme: ITheme): Int = theme.colorPrimary

    override fun getColorBehindNavigationBar(theme: ITheme): Int = theme.colorPrimary

    private fun initializeUi(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_lock)
        SystemBars.pad(findViewById(R.id.lock_layout), true, true, true)
        
        pinLayout = findViewById(R.id.pin_layout)
        pinHelpTextView = findViewById(R.id.pin_help_text_view)
        val indicatorDotsView: IndicatorDots = findViewById(R.id.indicator_dots)
        pinLockView = findViewById(R.id.pin_lock_view)
        
        sequenceLayout = findViewById(R.id.sequence_layout)
        sequenceHelpTextView = findViewById(R.id.sequence_help_text_view)
        patternLockView = findViewById(R.id.pattern_lock_view)
        
        fingerprintLayout = findViewById(R.id.fingerprint_layout)
        fingerprintHelpTextView = findViewById(R.id.fingerprint_help_text_view)
        
        fingerprintLayout.setOnClickListener { maybeShowBiometricPrompt() }
        
        indicatorDotsView.pinLength = PIN_CODE_LENGTH
        pinLockView.pinLength = PIN_CODE_LENGTH
        pinLockView.attachIndicatorDots(indicatorDotsView)
        pinLockView.setPinLockListener(object : PinLockListener {
            override fun onComplete(pin: String) {
                onPinCodeComplete(pin)
            }
            override fun onEmpty() {}
            override fun onPinChange(pinLength: Int, intermediatePin: String) {}
        })
        
        patternLockView.addPatternLockListener(object : PatternLockViewListener {
            override fun onStarted() {}
            override fun onProgress(progressPattern: MutableList<PatternLockView.Dot>?) {}
            override fun onComplete(pattern: MutableList<PatternLockView.Dot>?) {
                pattern?.let {
                    onPatternComplete(PatternLockUtils.patternToString(patternLockView, it))
                }
            }
            override fun onCleared() {}
        })
        
        if (savedInstanceState == null) {
            when (currentLockMode) {
                LockMode.PIN -> when (action) {
                    ACTION_UNLOCK, ACTION_DISABLE -> pinHelpTextView.setText(R.string.help_insert_pin_code)
                    ACTION_ENABLE -> {
                        currentStep = STEP_INSERT_NEW_CODE
                        pinHelpTextView.setText(R.string.help_create_new_pin_code)
                    }
                    ACTION_CHANGE_KEY, ACTION_CHANGE_MODE -> {
                        currentStep = STEP_INSERT_OLD_CODE
                        pinHelpTextView.setText(R.string.help_insert_old_pin_code)
                    }
                }
                LockMode.SEQUENCE -> when (action) {
                    ACTION_UNLOCK, ACTION_DISABLE -> sequenceHelpTextView.setText(R.string.help_insert_sequence)
                    ACTION_ENABLE -> {
                        currentStep = STEP_INSERT_NEW_CODE
                        sequenceHelpTextView.setText(R.string.help_create_new_sequence)
                    }
                    ACTION_CHANGE_KEY, ACTION_CHANGE_MODE -> {
                        currentStep = STEP_INSERT_OLD_CODE
                        sequenceHelpTextView.setText(R.string.help_insert_old_sequence)
                    }
                }
                LockMode.FINGERPRINT -> when (action) {
                    ACTION_UNLOCK, ACTION_DISABLE -> fingerprintHelpTextView.setText(R.string.help_insert_fingerprint)
                    ACTION_ENABLE, ACTION_CHANGE_KEY, ACTION_CHANGE_MODE -> {
                        currentStep = STEP_INSERT_OLD_CODE
                        fingerprintHelpTextView.setText(R.string.help_create_fingerprint)
                    }
                }
                else -> {}
            }
        }
        showLayout(currentLockMode)
    }

    private fun showLayout(lockMode: LockMode?) {
        pinLayout.visibility = if (lockMode == LockMode.PIN) View.VISIBLE else View.GONE
        sequenceLayout.visibility = if (lockMode == LockMode.SEQUENCE) View.VISIBLE else View.GONE
        fingerprintLayout.visibility = if (lockMode == LockMode.FINGERPRINT) View.VISIBLE else View.GONE
    }

    private fun maybeShowBiometricPrompt() {
        if (!isResumedState || currentLockMode != LockMode.FINGERPRINT || action == ACTION_CHANGE_KEY) {
            return
        }
        val status = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (status == BiometricManager.BIOMETRIC_SUCCESS) {
            biometricPrompt.authenticate(promptInfo)
        } else {
            fingerprintHelpTextView.setText(R.string.help_fingerprint_not_initialized)
        }
    }

    private fun onPinCodeComplete(code: String) {
        when (action) {
            ACTION_UNLOCK -> if (TextUtils.equals(code, PreferenceManager.getCurrentLockCode())) {
                PreferenceManager.setLastLockTime(System.currentTimeMillis())
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                pinLockView.resetPinLockView()
                pinHelpTextView.setText(R.string.help_insert_pin_code_failed)
            }
            ACTION_DISABLE -> if (TextUtils.equals(code, PreferenceManager.getCurrentLockCode())) {
                PreferenceManager.setCurrentLockMode(LockMode.NONE)
                PreferenceManager.setLastLockTime(System.currentTimeMillis())
                PreferenceManager.setCurrentLockCode(null)
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                pinLockView.resetPinLockView()
                pinHelpTextView.setText(R.string.help_insert_pin_code_failed)
            }
            ACTION_ENABLE -> if (currentStep == STEP_INSERT_NEW_CODE) {
                newCode = code
                currentStep = STEP_VERIFY_NEW_CODE
                pinLockView.resetPinLockView()
                pinHelpTextView.setText(R.string.help_verify_created_pin_code)
            } else if (currentStep == STEP_VERIFY_NEW_CODE) {
                if (TextUtils.equals(newCode, code)) {
                    PreferenceManager.setCurrentLockMode(LockMode.PIN)
                    PreferenceManager.setCurrentLockCode(code)
                    PreferenceManager.setLastLockTime(System.currentTimeMillis())
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    currentStep = STEP_INSERT_NEW_CODE
                    pinLockView.resetPinLockView()
                    pinHelpTextView.setText(R.string.help_verify_created_pin_code_failed)
                }
            }
            ACTION_CHANGE_KEY -> if (currentStep == STEP_INSERT_OLD_CODE) {
                if (TextUtils.equals(code, PreferenceManager.getCurrentLockCode())) {
                    currentStep = STEP_INSERT_NEW_CODE
                    pinLockView.resetPinLockView()
                    pinHelpTextView.setText(R.string.help_create_new_pin_code)
                } else {
                    pinLockView.resetPinLockView()
                    pinHelpTextView.setText(R.string.help_insert_pin_code_failed)
                }
            } else if (currentStep == STEP_INSERT_NEW_CODE) {
                newCode = code
                currentStep = STEP_VERIFY_NEW_CODE
                pinLockView.resetPinLockView()
                pinHelpTextView.setText(R.string.help_verify_created_pin_code)
            } else if (currentStep == STEP_VERIFY_NEW_CODE) {
                if (TextUtils.equals(newCode, code)) {
                    PreferenceManager.setCurrentLockMode(LockMode.PIN)
                    PreferenceManager.setCurrentLockCode(code)
                    PreferenceManager.setLastLockTime(System.currentTimeMillis())
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    currentStep = STEP_INSERT_NEW_CODE
                    pinLockView.resetPinLockView()
                    pinHelpTextView.setText(R.string.help_verify_created_pin_code_failed)
                }
            }
            ACTION_CHANGE_MODE -> if (currentStep == STEP_INSERT_OLD_CODE) {
                if (TextUtils.equals(code, PreferenceManager.getCurrentLockCode())) {
                    currentStep = STEP_INSERT_NEW_CODE
                    currentLockMode = targetLockMode
                    if (currentLockMode == LockMode.SEQUENCE) {
                        sequenceHelpTextView.setText(R.string.help_create_new_sequence)
                    } else if (currentLockMode == LockMode.FINGERPRINT) {
                        fingerprintHelpTextView.setText(R.string.help_create_fingerprint)
                    }
                    showLayout(currentLockMode)
                    maybeShowBiometricPrompt()
                } else {
                    pinHelpTextView.setText(R.string.help_insert_pin_code_failed)
                }
                pinLockView.resetPinLockView()
            } else if (currentStep == STEP_INSERT_NEW_CODE) {
                newCode = code
                currentStep = STEP_VERIFY_NEW_CODE
                pinLockView.resetPinLockView()
                pinHelpTextView.setText(R.string.help_verify_created_pin_code)
            } else if (currentStep == STEP_VERIFY_NEW_CODE) {
                if (TextUtils.equals(newCode, code)) {
                    PreferenceManager.setCurrentLockMode(LockMode.PIN)
                    PreferenceManager.setCurrentLockCode(code)
                    PreferenceManager.setLastLockTime(System.currentTimeMillis())
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    currentStep = STEP_INSERT_NEW_CODE
                    pinLockView.resetPinLockView()
                    pinHelpTextView.setText(R.string.help_verify_created_pin_code_failed)
                }
            }
        }
    }

    private fun onPatternComplete(pattern: String) {
        when (action) {
            ACTION_UNLOCK -> if (TextUtils.equals(pattern, PreferenceManager.getCurrentLockCode())) {
                PreferenceManager.setLastLockTime(System.currentTimeMillis())
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                patternLockView.setViewMode(PatternLockView.PatternViewMode.WRONG)
                sequenceHelpTextView.setText(R.string.help_insert_sequence_failed)
            }
            ACTION_DISABLE -> if (TextUtils.equals(pattern, PreferenceManager.getCurrentLockCode())) {
                PreferenceManager.setCurrentLockMode(LockMode.NONE)
                PreferenceManager.setLastLockTime(System.currentTimeMillis())
                PreferenceManager.setCurrentLockCode(null)
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                patternLockView.setViewMode(PatternLockView.PatternViewMode.WRONG)
                sequenceHelpTextView.setText(R.string.help_insert_sequence_failed)
            }
            ACTION_ENABLE -> if (currentStep == STEP_INSERT_NEW_CODE) {
                newCode = pattern
                currentStep = STEP_VERIFY_NEW_CODE
                patternLockView.clearPattern()
                sequenceHelpTextView.setText(R.string.help_verify_created_sequence)
            } else if (currentStep == STEP_VERIFY_NEW_CODE) {
                if (TextUtils.equals(newCode, pattern)) {
                    PreferenceManager.setCurrentLockMode(LockMode.SEQUENCE)
                    PreferenceManager.setCurrentLockCode(pattern)
                    PreferenceManager.setLastLockTime(System.currentTimeMillis())
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    currentStep = STEP_INSERT_NEW_CODE
                    patternLockView.setViewMode(PatternLockView.PatternViewMode.WRONG)
                    sequenceHelpTextView.setText(R.string.help_verify_created_sequence_failed)
                }
            }
            ACTION_CHANGE_KEY -> if (currentStep == STEP_INSERT_OLD_CODE) {
                if (TextUtils.equals(pattern, PreferenceManager.getCurrentLockCode())) {
                    currentStep = STEP_INSERT_NEW_CODE
                    patternLockView.clearPattern()
                    sequenceHelpTextView.setText(R.string.help_create_new_sequence)
                } else {
                    patternLockView.setViewMode(PatternLockView.PatternViewMode.WRONG)
                    sequenceHelpTextView.setText(R.string.help_insert_sequence_failed)
                }
            } else if (currentStep == STEP_INSERT_NEW_CODE) {
                newCode = pattern
                currentStep = STEP_VERIFY_NEW_CODE
                patternLockView.clearPattern()
                sequenceHelpTextView.setText(R.string.help_verify_created_sequence)
            } else if (currentStep == STEP_VERIFY_NEW_CODE) {
                if (TextUtils.equals(newCode, pattern)) {
                    PreferenceManager.setCurrentLockMode(LockMode.SEQUENCE)
                    PreferenceManager.setCurrentLockCode(pattern)
                    PreferenceManager.setLastLockTime(System.currentTimeMillis())
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    currentStep = STEP_INSERT_NEW_CODE
                    patternLockView.setViewMode(PatternLockView.PatternViewMode.WRONG)
                    sequenceHelpTextView.setText(R.string.help_verify_created_sequence_failed)
                }
            }
            ACTION_CHANGE_MODE -> if (currentStep == STEP_INSERT_OLD_CODE) {
                if (TextUtils.equals(pattern, PreferenceManager.getCurrentLockCode())) {
                    currentStep = STEP_INSERT_NEW_CODE
                    currentLockMode = targetLockMode
                    if (currentLockMode == LockMode.PIN) {
                        pinHelpTextView.setText(R.string.help_create_new_pin_code)
                    } else if (currentLockMode == LockMode.FINGERPRINT) {
                        fingerprintHelpTextView.setText(R.string.help_create_fingerprint)
                    }
                    patternLockView.clearPattern()
                    showLayout(currentLockMode)
                    maybeShowBiometricPrompt()
                } else {
                    patternLockView.setViewMode(PatternLockView.PatternViewMode.WRONG)
                    sequenceHelpTextView.setText(R.string.help_insert_pin_code_failed)
                }
            } else if (currentStep == STEP_INSERT_NEW_CODE) {
                newCode = pattern
                currentStep = STEP_VERIFY_NEW_CODE
                patternLockView.clearPattern()
                sequenceHelpTextView.setText(R.string.help_verify_created_sequence)
            } else if (currentStep == STEP_VERIFY_NEW_CODE) {
                if (TextUtils.equals(newCode, pattern)) {
                    PreferenceManager.setCurrentLockMode(LockMode.SEQUENCE)
                    PreferenceManager.setCurrentLockCode(pattern)
                    PreferenceManager.setLastLockTime(System.currentTimeMillis())
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    currentStep = STEP_INSERT_NEW_CODE
                    patternLockView.setViewMode(PatternLockView.PatternViewMode.WRONG)
                    sequenceHelpTextView.setText(R.string.help_verify_created_sequence_failed)
                }
            }
        }
    }

    private fun onFingerprintScan(recognized: Boolean, errorType: Int, message: CharSequence?) {
        when (action) {
            ACTION_UNLOCK -> if (recognized) {
                PreferenceManager.setLastLockTime(System.currentTimeMillis())
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                fingerprintHelpTextView.setText(R.string.help_insert_fingerprint_failed)
            }
            ACTION_DISABLE -> if (recognized) {
                PreferenceManager.setCurrentLockMode(LockMode.NONE)
                PreferenceManager.setLastLockTime(System.currentTimeMillis())
                PreferenceManager.setCurrentLockCode(null)
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                fingerprintHelpTextView.setText(R.string.help_insert_fingerprint_failed)
            }
            ACTION_ENABLE -> if (recognized) {
                PreferenceManager.setCurrentLockMode(LockMode.FINGERPRINT)
                PreferenceManager.setLastLockTime(System.currentTimeMillis())
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                fingerprintHelpTextView.setText(R.string.help_insert_fingerprint_failed)
            }
            ACTION_CHANGE_KEY -> {
                // not supported, use android settings to change or add another fingerprint
            }
            ACTION_CHANGE_MODE -> if (currentStep == STEP_INSERT_OLD_CODE) {
                if (recognized) {
                    currentStep = STEP_INSERT_NEW_CODE
                    currentLockMode = targetLockMode
                    if (currentLockMode == LockMode.PIN) {
                        pinHelpTextView.setText(R.string.help_create_new_pin_code)
                    } else if (currentLockMode == LockMode.SEQUENCE) {
                        sequenceHelpTextView.setText(R.string.help_create_new_sequence)
                    }
                    showLayout(currentLockMode)
                } else {
                    fingerprintHelpTextView.setText(R.string.help_insert_fingerprint_failed)
                }
            } else if (currentStep == STEP_INSERT_NEW_CODE) {
                if (recognized) {
                    PreferenceManager.setCurrentLockMode(LockMode.FINGERPRINT)
                    PreferenceManager.setLastLockTime(System.currentTimeMillis())
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    currentStep = STEP_INSERT_NEW_CODE
                    fingerprintHelpTextView.setText(R.string.help_insert_fingerprint_failed)
                }
            }
        }
    }

    companion object {
        const val MODE = "LockActivity::Arguments::Mode"
        const val ACTION = "LockActivity::Arguments::Action"

        private const val SS_NEW_CODE = "LockActivity::SavedState::NewCode"
        private const val SS_CURRENT_STEP = "LockActivity::SavedState::CurrentStep"
        private const val SS_CURRENT_LOCK_MODE = "LockActivity::SavedState::CurrentLockMode"

        const val ACTION_UNLOCK = 0
        const val ACTION_DISABLE = 1
        const val ACTION_ENABLE = 2
        const val ACTION_CHANGE_KEY = 3
        const val ACTION_CHANGE_MODE = 4

        private const val PIN_CODE_LENGTH = 5

        private const val STEP_INSERT_OLD_CODE = 1
        private const val STEP_INSERT_NEW_CODE = 2
        private const val STEP_VERIFY_NEW_CODE = 3

        @JvmStatic
        fun unlock(activity: Activity): Intent {
            val intent = Intent(activity, LockActivity::class.java)
            intent.putExtra(ACTION, ACTION_UNLOCK)
            return intent
        }

        @JvmStatic
        fun enableLock(activity: Activity, lockMode: LockMode): Intent {
            val intent = Intent(activity, LockActivity::class.java)
            intent.putExtra(MODE, lockMode)
            intent.putExtra(ACTION, ACTION_ENABLE)
            return intent
        }

        @JvmStatic
        fun disableLock(activity: Activity): Intent {
            val intent = Intent(activity, LockActivity::class.java)
            intent.putExtra(ACTION, ACTION_DISABLE)
            return intent
        }

        @JvmStatic
        fun changeKey(activity: Activity): Intent {
            val intent = Intent(activity, LockActivity::class.java)
            intent.putExtra(ACTION, ACTION_CHANGE_KEY)
            return intent
        }

        @JvmStatic
        fun changeMode(activity: Activity, lockMode: LockMode): Intent {
            val intent = Intent(activity, LockActivity::class.java)
            intent.putExtra(MODE, lockMode)
            intent.putExtra(ACTION, ACTION_CHANGE_MODE)
            return intent
        }
    }
}
