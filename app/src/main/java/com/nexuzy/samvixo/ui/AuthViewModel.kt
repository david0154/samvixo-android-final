package com.nexuzy.samvixo.ui

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.FirebaseAuthSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class OtpSent(val verificationId: String) : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    // Enable Recaptcha / App Check Settings
    init {
        auth.firebaseAuthSettings.apply {
            // Force Recaptcha flow for enhanced security
            // In production, this allows Firebase to automatically handle bot detection
            // via SafetyNet or Play Integrity
            forceRecaptchaFlowForTesting(false) 
        }
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun sendOtp(phoneNumber: String, activity: android.app.Activity) {
        _authState.value = AuthState.Loading
        
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                signInWithCredential(credential)
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                _authState.value = AuthState.Error(e.message ?: "Verification failed. Check network or phone format.")
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                _authState.value = AuthState.OtpSent(verificationId)
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(verificationId: String, otp: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId, otp)
        signInWithCredential(credential)
    }

    private fun signInWithCredential(credential: com.google.firebase.auth.PhoneAuthCredential) {
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                _authState.value = AuthState.Success
            } else {
                _authState.value = AuthState.Error(task.exception?.message ?: "Sign in failed")
            }
        }
    }
}
