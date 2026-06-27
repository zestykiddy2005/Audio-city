package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.error.CrashHandler
import com.example.ui.error.ErrorBoundary
import com.example.ui.error.CrashRecoveryScreen
import com.example.ui.screens.MainAppContainer
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Install system-wide uncaught exception handler
        CrashHandler.install(applicationContext)
        
        enableEdgeToEdge()
        
        // 2. Check if returning from a crash process redirect
        val isCrash = intent.getBooleanExtra(CrashHandler.EXTRA_IS_CRASH, false)
        val errorMessage = intent.getStringExtra(CrashHandler.EXTRA_ERROR_MESSAGE) ?: "Unknown crash"
        val errorStacktrace = intent.getStringExtra(CrashHandler.EXTRA_ERROR_STACKTRACE) ?: ""

        setContent {
            MyApplicationTheme {
                if (isCrash) {
                    CrashRecoveryScreen(
                        errorMessage = errorMessage,
                        errorStacktrace = errorStacktrace,
                        onRestart = {
                            // Fresh relaunch
                            val restartIntent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(restartIntent)
                        },
                        onClearCache = {
                            // Wipe cache files & settings
                            try {
                                val sharedPrefs = getSharedPreferences("AudioCityPrefs", Context.MODE_PRIVATE)
                                sharedPrefs.edit().clear().commit()
                                cacheDir.deleteRecursively()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            // Clean relaunch
                            val restartIntent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(restartIntent)
                        }
                    )
                } else {
                    // Local Compose error boundary wrapper
                    ErrorBoundary {
                        MainAppContainer(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
