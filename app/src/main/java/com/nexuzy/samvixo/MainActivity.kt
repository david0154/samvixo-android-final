package com.nexuzy.samvixo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexuzy.samvixo.ui.MainScreen
import com.nexuzy.samvixo.ui.theme.SamvixoTheme

class MainActivity : ComponentActivity() {

    private val showLockScreen = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppLockManager.clearSessionUnlock(this)

        setContent {
            SamvixoTheme {
                val lockVisible by showLockScreen
                if (lockVisible) {
                    AppLockScreen(
                        onUnlocked = {
                            AppLockManager.markSessionUnlocked(this@MainActivity)
                            showLockScreen.value = false
                        }
                    )
                } else {
                    MainScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (AppLockManager.shouldShowLock(this)) {
            showLockScreen.value = true
        }
    }

    override fun onStop() {
        super.onStop()
        if (AppLockManager.isLockEnabled(this)) {
            AppLockManager.clearSessionUnlock(this)
        }
    }
}

@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "Enter PIN to unlock",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.take(6) },
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error.isNotEmpty()
            )
            if (error.isNotEmpty()) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (AppLockManager.verifyPin(context, pin)) {
                        onUnlocked()
                    } else {
                        error = "Incorrect PIN"
                        pin = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Unlock")
            }
        }
    }
}
