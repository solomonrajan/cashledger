/*
 * Copyright (c) 2018.
 * Copyright (c) 2026 solomonrajan.
 *
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

/**
 * Created by andrea on 24/07/18.
 */
class LockActivity : ThemedActivity() {

    private var mAction = ACTION_UNLOCK
    private var mTargetLockMode: LockMode = LockMode.NONE

    private var mPinLayout: ViewGroup? = null
    private var mSequenceLayout: ViewGroup? = null
    private var mFingerprintLayout: ViewGroup? = null

    private var mPinHelpTextView: TextView? = null
    private var mPinLockView: PinLockView? = null

    private var mSequenceHelpTextView: TextView? = null
    private var mPatternLockView: PatternLockView? = null

    private var mFingerprintHelpTextView: TextView? = null

    private var mNewCode: String? = null
    private var mCurrentStep = 0
    private var mCurrentLockMode: LockMode? = null
    private var mBiometricPrompt: BiometricPrompt? = null
    private var mPromptInfo: BiometricPrompt.PromptInfo? = null
    private var mResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unpackIntent(intent, savedInstanceState)
        initializeUi(savedInstanceState)
        mBiometricPrompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onFingerprintScan(true, 0, null)
                }

                override fun onAuthenticationFailed() {
                    // A biometric was presented but not recognized. The system prompt stays open for
                    // retry; just reflect the failed attempt in the in-app help text.
                    onFingerprintScan(false, 0, null)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Terminal error (cancel, lockout, hardware issue). Fail soft: show help text and
                    // let the user retry by tapping the fingerprint area. Never crash or disable the lock.
                    onFingerprintScan(false, errorCode, errString)
                }
            }
        )
        mPromptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setNegativeButtonText(getString(android.R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    }

    override fun onResume() {
        super.onResume()
        mResumed = true
        maybeShowBiometricPrompt()
    }

    override fun onPause() {
        super.onPause()
        mResumed = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SS_NEW_CODE, mNewCode)
        outState.putInt(SS_CURRENT_STEP, mCurrentStep)
        outState.putSerializable(SS_CURRENT_LOCK_MODE, mCurrentLockMode)
    }

    private fun unpackIntent(intent: Intent?, savedInstanceState: Bundle?) {
        mAction = intent?.getIntExtra(ACTION, ACTION_UNLOCK) ?: ACTION_UNLOCK
        mTargetLockMode = (intent?.getSerializableExtra(MODE) as? LockMode) ?: LockMode.NONE
        if (savedInstanceState != null) {
            mNewCode = savedInstanceState.getString(SS_NEW_CODE)
            mCurrentStep = savedInstanceState.getInt(SS_CURRENT_STEP)
            mCurrentLockMode = savedInstanceState.getSerializable(SS_CURRENT_LOCK_MODE) as? LockMode
        } else {
            when (mAction) {
                ACTION_UNLOCK,
                ACTION_DISABLE,
                ACTION_CHANGE_KEY,
                ACTION_CHANGE_MODE -> {
                    mCurrentLockMode = PreferenceManager.getCurrentLockMode()
                }
                ACTION_ENABLE -> {
                    mCurrentLockMode = mTargetLockMode
                }
            }
        }
    }

    /**
     * This screen has no toolbar and no list. It paints the primary color over the whole window,
     * so that is what is behind both bars.
     */
    override fun getColorBehindStatusBar(theme: ITheme): Int {
        return theme.colorPrimary
    }

    override fun getColorBehindNavigationBar(theme: ITheme): Int {
        return theme.colorPrimary
    }

    private fun initializeUi(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_lock)
        SystemBars.pad(findViewById(R.id.lock_layout), true, true, true)
        mPinLayout = findViewById(R.id.pin_layout)
        mPinHelpTextView = findViewById(R.id.pin_help_text_view)
        val indicatorDotsView: IndicatorDots = findViewById(R.id.indicator_dots)
        mPinLockView = findViewById(R.id.pin_lock_view)
        mSequenceLayout = findViewById(R.id.sequence_layout)
        mSequenceHelpTextView = findViewById(R.id.sequence_help_text_view)
        mPatternLockView = findViewById(R.id.pattern_lock_view)
        mFingerprintLayout = findViewById(R.id.fingerprint_layout)
        mFingerprintHelpTextView = findViewById(R.id.fingerprint_help_text_view)

        // tapping the fingerprint area re-shows the system biometric prompt (e.g. after a cancel)
        mFingerprintLayout?.setOnClickListener { maybeShowBiometricPrompt() }

        // enable ui
        indicatorDotsView.pinLength = PIN_CODE_LENGTH
        mPinLockView?.setPinLength(PIN_CODE_LENGTH)
        mPinLockView?.attachIndicatorDots(indicatorDotsView)
        mPinLockView?.setPinLockListener(object : PinLockListener {
            override fun onComplete(pin: String?) {
                onPinCodeComplete(pin ?: "")
            }

            override fun onEmpty() {
                // not used
            }

            override fun onPinChange(pinLength: Int, intermediatePin: String?) {
                // not used
            }
        })
        mPatternLockView?.addPatternLockListener(object : PatternLockViewListener {
            override fun onStarted() {
                // not used
            }

            override fun onProgress(progressPattern: MutableList<PatternLockView.Dot>?) {
                // not used
            }

            override fun onComplete(pattern: MutableList<PatternLockView.Dot>?) {
                onPatternComplete(PatternLockUtils.patternToString(mPatternLockView, pattern))
            }

            override fun onCleared() {
                // not used
            }
        })

        // setup the views
        if (savedInstanceState == null) {
            when (mCurrentLockMode) {
                LockMode.PIN -> {
                    when (mAction) {
                        ACTION_UNLOCK,
                        ACTION_DISABLE -> {
                            mPinHelpTextView?.setText(R.string.help_insert_pin_code)
                        }
                        ACTION_ENABLE -> {
                            mCurrentStep = STEP_INSERT_NEW_CODE
                            mPinHelpTextView?.setText(R.string.help_create_new_pin_code)
                        }
                        ACTION_CHANGE_KEY,
                        ACTION_CHANGE_MODE -> {
                            mCurrentStep = STEP_INSERT_OLD_CODE
                            mPinHelpTextView?.setText(R.string.help_insert_old_pin_code)
                        }
                    }
                }
                LockMode.SEQUENCE -> {
                    when (mAction) {
                        ACTION_UNLOCK,
                        ACTION_DISABLE -> {
                            mSequenceHelpTextView?.setText(R.string.help_insert_sequence)
                        }
                        ACTION_ENABLE -> {
                            mCurrentStep = STEP_INSERT_NEW_CODE
                            mSequenceHelpTextView?.setText(R.string.help_create_new_sequence)
                        }
                        ACTION_CHANGE_KEY,
                        ACTION_CHANGE_MODE -> {
                            mCurrentStep = STEP_INSERT_OLD_CODE
                            mSequenceHelpTextView?.setText(R.string.help_insert_old_sequence)
                        }
                    }
                }
                LockMode.FINGERPRINT -> {
                    when (mAction) {
                        ACTION_UNLOCK,
                        ACTION_DISABLE -> {
                            mFingerprintHelpTextView?.setText(R.string.help_insert_fingerprint)
                        }
                        ACTION_ENABLE,
                        ACTION_CHANGE_KEY,
                        ACTION_CHANGE_MODE -> {
                            mCurrentStep = STEP_INSERT_OLD_CODE
                            mFingerprintHelpTextView?.setText(R.string.help_create_fingerprint)
                        }
                    }
                }
                else -> Unit
            }
        }
        showLayout(mCurrentLockMode)
    }

    private fun showLayout(lockMode: LockMode?) {
        mPinLayout?.visibility = if (lockMode == LockMode.PIN) View.VISIBLE else View.GONE
        mSequenceLayout?.visibility = if (lockMode == LockMode.SEQUENCE) View.VISIBLE else View.GONE
        mFingerprintLayout?.visibility = if (lockMode == LockMode.FINGERPRINT) View.VISIBLE else View.GONE
    }

    /**
     * Shows the system biometric prompt, but only when fingerprint is the active lock mode and the
     * current flow actually verifies with a fingerprint scan (change-key is not supported for
     * fingerprint). If biometrics are configured but currently unavailable, it fails soft with the
     * existing help text instead of crashing or disabling the lock.
     */
    private fun maybeShowBiometricPrompt() {
        if (!mResumed || mCurrentLockMode != LockMode.FINGERPRINT || mAction == ACTION_CHANGE_KEY) {
            return
        }
        val status = BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        )
        if (status == BiometricManager.BIOMETRIC_SUCCESS) {
            val prompt = mBiometricPrompt
            val info = mPromptInfo
            if (prompt != null && info != null) {
                prompt.authenticate(info)
            }
        } else {
            mFingerprintHelpTextView?.setText(R.string.help_fingerprint_not_initialized)
        }
    }

    private fun onPinCodeComplete(code: String) {
        when (mAction) {
            ACTION_UNLOCK -> {
                if (TextUtils.equals(code, PreferenceManager.getCurrentLockCode())) {
                    PreferenceManager.setLastLockTime(System.currentTimeMillis())
                    setResult(RESULT_OK)
                    finish()
                } else {
                    mPinLockView?.resetPinLockView()
                    mPinHelpTextView?.setText(R.string.help_insert_pin_code_failed)
                }
            }
            ACTION_DISABLE -> {
                if (TextUtils.equals(code, PreferenceManager.getCurrentLockCode())) {
                    PreferenceManager.setCurrentLockMode(LockMode.NONE)
                    PreferenceManager.setLastLockTime(System.currentTimeMillis())
                    PreferenceManager.setCurrentLockCode(null)
                    setResult(RESULT_OK)
                    finish()
                } else {
                    mPinLockView?.resetPinLockView()
                    mPinHelpTextView?.setText(R.string.help_insert_pin_code_failed)
                }
            }
            ACTION_ENABLE -> {
                if (mCurrentStep == STEP_INSERT_NEW_CODE) {
                    mNewCode = code
                    mCurrentStep = STEP_VERIFY_NEW_CODE
                    mPinLockView?.resetPinLockView()
                    mPinHelpTextView?.setText(R.string.help_verify_created_pin_code)
                } else if (mCurrentStep == STEP_VERIFY_NEW_CODE) {
                    if (TextUtils.equals(mNewCode, code)) {
                        PreferenceManager.setCurrentLockMode(LockMode.PIN)
                        PreferenceManager.setCurrentLockCode(code)
                        PreferenceManager.setLastLockTime(System.currentTimeMillis())
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        // the user has inserted two different codes, simply alert him and restart
                        mCurrentStep = STEP_INSERT_NEW_CODE
                        mPinLockView?.resetPinLockView()
                        mPinHelpTextView?.setText(R.string.help_verify_created_pin_code_failed)
                    }
                }
            }
            ACTION_CHANGE_KEY -> {
                if (mCurrentStep == STEP_INSERT_OLD_CODE) {
                    if (TextUtils.equals(code, PreferenceManager.getCurrentLockCode())) {
                        mCurrentStep = STEP_INSERT_NEW_CODE
                        mPinLockView?.resetPinLockView()
                        mPinHelpTextView?.setText(R.string.help_create_new_pin_code)
                    } else {
                        mPinLockView?.resetPinLockView()
                        mPinHelpTextView?.setText(R.string.help_insert_pin_code_failed)
                    }
                } else if (mCurrentStep == STEP_INSERT_NEW_CODE) {
                    mNewCode = code
                    mCurrentStep = STEP_VERIFY_NEW_CODE
                    mPinLockView?.resetPinLockView()
                    mPinHelpTextView?.setText(R.string.help_verify_created_pin_code)
                } else if (mCurrentStep == STEP_VERIFY_NEW_CODE) {
                    if (TextUtils.equals(mNewCode, code)) {
                        PreferenceManager.setCurrentLockMode(LockMode.PIN)
                        PreferenceManager.setCurrentLockCode(code)
                        PreferenceManager.setLastLockTime(System.currentTimeMillis())
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        // the user has inserted two different codes, simply alert him and restart
                        mCurrentStep = STEP_INSERT_NEW_CODE
                        mPinLockView?.resetPinLockView()
                        mPinHelpTextView?.setText(R.string.help_verify_created_pin_code_failed)
                    }
                }
            }
            ACTION_CHANGE_MODE -> {
                if (mCurrentStep == STEP_INSERT_OLD_CODE) {
                    if (TextUtils.equals(code, PreferenceManager.getCurrentLockCode())) {
                        mCurrentStep = STEP_INSERT_NEW_CODE
                        mCurrentLockMode = mTargetLockMode
                        if (mCurrentLockMode == LockMode.SEQUENCE) {
                            mSequenceHelpTextView?.setText(R.string.help_create_new_sequence)
                        } else if (mCurrentLockMode == LockMode.FINGERPRINT) {
                            mFingerprintHelpTextView?.setText(R.string.help_create_fingerprint)
                        }
                        showLayout(mCurrentLockMode)
                        maybeShowBiometricPrompt()
                    } else {
                        mPinHelpTextView?.setText(R.string.help_insert_pin_code_failed)
                    }
                    mPinLockView?.resetPinLockView()
                } else if (mCurrentStep == STEP_INSERT_NEW_CODE) {
                    mNewCode = code
                    mCurrentStep = STEP_VERIFY_NEW_CODE
                    mPinLockView?.resetPinLockView()
                    mPinHelpTextView?.setText(R.string.help_verify_created_pin_code)
                } else if (mCurrentStep == STEP_VERIFY_NEW_CODE) {
                    if (TextUtils.equals(mNewCode, code)) {
                        PreferenceManager.setCurrentLockMode(LockMode.PIN)
                        PreferenceManager.setCurrentLockCode(code)
                        PreferenceManager.setLastLockTime(System.currentTimeMillis())
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        // the user has inserted two different codes, simply alert him and restart
                        mCurrentStep = STEP_INSERT_NEW_CODE
                        mPinLockView?.resetPinLockView()
                        mPinHelpTextView?.setText(R.string.help_verify_created_pin_code_failed)
                    }
                }
            }
        }
    }

    private fun onPatternComplete(pattern: String) {
        when (mAction) {
            ACTION_UNLOCK -> {
                if (TextUtils.equals(pattern, PreferenceManager.getCurrentLockCode())) {
                    PreferenceManager.setLastLockTime(System.currentTimeMillis())
                    setResult(RESULT_OK)
                    finish()
                } else {
                    mPatternLockView?.setViewMode(PatternLockView.PatternViewMode.WRONG)
                    mSequenceHelpTextView?.setText(R.string.help_insert_sequence_failed)
                }
            }
            ACTION_DISABLE -> {
                if (TextUtils.equals(pattern, PreferenceManager.getCurrentLockCode())) {
                    PreferenceManager.setCurrentLockMode(LockMode.NONE)
                    PreferenceManager.setLastLockTime(System.currentTimeMillis())
                    PreferenceManager.setCurrentLockCode(null)
                    setResult(RESULT_OK)
                    finish()
                } else {
                    mPatternLockView?.setViewMode(PatternLockView.PatternViewMode.WRONG)
                    mSequenceHelpTextView?.setText(R.string.help_insert_sequence_failed)
                }
            }
            ACTION_ENABLE -> {
                if (mCurrentStep == STEP_INSERT_NEW_CODE) {
                    mNewCode = pattern
                    mCurrentStep = STEP_VERIFY_NEW_CODE
                    mPatternLockView?.clearPattern()
                    mSequenceHelpTextView?.setText(R.string.help_verify_created_sequence)
                } else if (mCurrentStep == STEP_VERIFY_NEW_CODE) {
                    if (TextUtils.equals(mNewCode, pattern)) {
                        PreferenceManager.setCurrentLockMode(LockMode.SEQUENCE)
                        PreferenceManager.setCurrentLockCode(pattern)
                        PreferenceManager.setLastLockTime(System.currentTimeMillis())
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        // the user has inserted two different codes, simply alert him and restart
                        mCurrentStep = STEP_INSERT_NEW_CODE
                        mPatternLockView?.setViewMode(PatternLockView.PatternViewMode.WRONG)
                        mSequenceHelpTextView?.setText(R.string.help_verify_created_sequence_failed)
                    }
                }
            }
            ACTION_CHANGE_KEY -> {
                if (mCurrentStep == STEP_INSERT_OLD_CODE) {
                    if (TextUtils.equals(pattern, PreferenceManager.getCurrentLockCode())) {
                        mCurrentStep = STEP_INSERT_NEW_CODE
                        mPatternLockView?.clearPattern()
                        mSequenceHelpTextView?.setText(R.string.help_create_new_sequence)
                    } else {
                        mPatternLockView?.setViewMode(PatternLockView.PatternViewMode.WRONG)
                        mSequenceHelpTextView?.setText(R.string.help_insert_sequence_failed)
                    }
                } else if (mCurrentStep == STEP_INSERT_NEW_CODE) {
                    mNewCode = pattern
                    mCurrentStep = STEP_VERIFY_NEW_CODE
                    mPatternLockView?.clearPattern()
                    mSequenceHelpTextView?.setText(R.string.help_verify_created_sequence)
                } else if (mCurrentStep == STEP_VERIFY_NEW_CODE) {
                    if (TextUtils.equals(mNewCode, pattern)) {
                        PreferenceManager.setCurrentLockMode(LockMode.SEQUENCE)
                        PreferenceManager.setCurrentLockCode(pattern)
                        PreferenceManager.setLastLockTime(System.currentTimeMillis())
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        // the user has inserted two different codes, simply alert him and restart
                        mCurrentStep = STEP_INSERT_NEW_CODE
                        mPatternLockView?.setViewMode(PatternLockView.PatternViewMode.WRONG)
                        mSequenceHelpTextView?.setText(R.string.help_verify_created_sequence_failed)
                    }
                }
            }
            ACTION_CHANGE_MODE -> {
                if (mCurrentStep == STEP_INSERT_OLD_CODE) {
                    if (TextUtils.equals(pattern, PreferenceManager.getCurrentLockCode())) {
                        mCurrentStep = STEP_INSERT_NEW_CODE
                        mCurrentLockMode = mTargetLockMode
                        if (mCurrentLockMode == LockMode.PIN) {
                            mPinHelpTextView?.setText(R.string.help_create_new_pin_code)
                        } else if (mCurrentLockMode == LockMode.FINGERPRINT) {
                            mFingerprintHelpTextView?.setText(R.string.help_create_fingerprint)
                        }
                        mPatternLockView?.clearPattern()
                        showLayout(mCurrentLockMode)
                        maybeShowBiometricPrompt()
                    } else {
                        mPatternLockView?.setViewMode(PatternLockView.PatternViewMode.WRONG)
                        mSequenceHelpTextView?.setText(R.string.help_insert_pin_code_failed)
                    }
                } else if (mCurrentStep == STEP_INSERT_NEW_CODE) {
                    mNewCode = pattern
                    mCurrentStep = STEP_VERIFY_NEW_CODE
                    mPatternLockView?.clearPattern()
                    mSequenceHelpTextView?.setText(R.string.help_verify_created_sequence)
                } else if (mCurrentStep == STEP_VERIFY_NEW_CODE) {
                    if (TextUtils.equals(mNewCode, pattern)) {
                        PreferenceManager.setCurrentLockMode(LockMode.SEQUENCE)
                        PreferenceManager.setCurrentLockCode(pattern)
                        PreferenceManager.setLastLockTime(System.currentTimeMillis())
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        // the user has inserted two different codes, simply alert him and restart
                        mCurrentStep = STEP_INSERT_NEW_CODE
                        mPatternLockView?.setViewMode(PatternLockView.PatternViewMode.WRONG)
                        mSequenceHelpTextView?.setText(R.string.help_verify_created_sequence_failed)
                    }
                }
            }
        }
    }

    private fun onFingerprintScan(recognized: Boolean, errorType: Int, message: CharSequence?) {
        when (mAction) {
            ACTION_UNLOCK -> {
                if (recognized) {
                    PreferenceManager.setLastLockTime(System.currentTimeMillis())
                    setResult(RESULT_OK)
                    finish()
                } else {
                    mFingerprintHelpTextView?.setText(R.string.help_insert_fingerprint_failed)
                }
            }
            ACTION_DISABLE -> {
                if (recognized) {
                    PreferenceManager.setCurrentLockMode(LockMode.NONE)
                    PreferenceManager.setLastLockTime(System.currentTimeMillis())
                    PreferenceManager.setCurrentLockCode(null)
                    setResult(RESULT_OK)
                    finish()
                } else {
                    mFingerprintHelpTextView?.setText(R.string.help_insert_fingerprint_failed)
                }
            }
            ACTION_ENABLE -> {
                if (recognized) {
                    PreferenceManager.setCurrentLockMode(LockMode.FINGERPRINT)
                    PreferenceManager.setLastLockTime(System.currentTimeMillis())
                    setResult(RESULT_OK)
                    finish()
                } else {
                    mFingerprintHelpTextView?.setText(R.string.help_insert_fingerprint_failed)
                }
            }
            ACTION_CHANGE_KEY -> {
                // not supported, use android settings to change or add another fingerprint
            }
            ACTION_CHANGE_MODE -> {
                if (mCurrentStep == STEP_INSERT_OLD_CODE) {
                    if (recognized) {
                        mCurrentStep = STEP_INSERT_NEW_CODE
                        mCurrentLockMode = mTargetLockMode
                        if (mCurrentLockMode == LockMode.PIN) {
                            mPinHelpTextView?.setText(R.string.help_create_new_pin_code)
                        } else if (mCurrentLockMode == LockMode.SEQUENCE) {
                            mSequenceHelpTextView?.setText(R.string.help_create_new_sequence)
                        }
                        showLayout(mCurrentLockMode)
                    } else {
                        mFingerprintHelpTextView?.setText(R.string.help_insert_fingerprint_failed)
                    }
                } else if (mCurrentStep == STEP_INSERT_NEW_CODE) {
                    if (recognized) {
                        PreferenceManager.setCurrentLockMode(LockMode.FINGERPRINT)
                        PreferenceManager.setLastLockTime(System.currentTimeMillis())
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        // the user has inserted two different codes, simply alert him and restart
                        mCurrentStep = STEP_INSERT_NEW_CODE
                        mFingerprintHelpTextView?.setText(R.string.help_insert_fingerprint_failed)
                    }
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

        /**
         * Default action if not specified, it will prompt the user to insert the key
         * and return an intent with RESULT_OK if the user entered the correct secret.
         */
        const val ACTION_UNLOCK = 0

        /**
         * This action will prompt the user to insert the current key to verify his
         * identity, then if the correct secret has been provided, the lock mode will
         * be set to DISABLED state and an intent with RESULT_OK will be returned.
         */
        const val ACTION_DISABLE = 1

        /**
         * This action will prompt the user to create a key using the specified MODE
         * and will verify it before setting the current lock mode to the specified
         * mode. An intent with RESULT_OK will be returned if the operation have
         * success.
         */
        const val ACTION_ENABLE = 2

        /**
         * This action will prompt the user to insert the current key to verify his
         * identity, then if the correct secret has been provided, it will be prompted
         * to create (and verify) a new key keeping the same lock mode. An intent with
         * RESULT_OK will be returned if the operation have success.
         */
        const val ACTION_CHANGE_KEY = 3

        /**
         * This action will prompt the user to insert the current key to verify his
         * identity, then if the correct secret has been provided, it will be prompted
         * to create (and verify) a new key using the provided MODE. An intent with
         * RESULT_OK will be returned if the operation have success.
         */
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
