package com.example.ui.error

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.ui.theme.*
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * CompositionLocal to allow deep-nested composables to manually trigger the global error boundary.
 */
val LocalErrorBoundary = staticCompositionLocalOf<ErrorBoundaryScope> {
    error("No ErrorBoundaryScope provided")
}

interface ErrorBoundaryScope {
    fun reportError(throwable: Throwable)
}

/**
 * Intercepts uncaught thread exceptions and redirects to the MainActivity crash screen.
 */
object CrashHandler {
    private const val TAG = "CrashHandler"
    const val EXTRA_IS_CRASH = "is_crash"
    const val EXTRA_ERROR_MESSAGE = "error_message"
    const val EXTRA_ERROR_STACKTRACE = "error_stacktrace"

    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        // Prevent installing multiple times
        if (defaultHandler?.javaClass?.name?.contains("CrashHandler") == true) return

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "Uncaught crash detected on thread: ${thread.name}", throwable)
                
                // Format stack trace
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stacktraceString = sw.toString()
                
                // Start fresh activity instance with crash metadata
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(EXTRA_IS_CRASH, true)
                    putExtra(EXTRA_ERROR_MESSAGE, throwable.localizedMessage ?: throwable.message ?: "Unknown crash")
                    putExtra(EXTRA_ERROR_STACKTRACE, stacktraceString)
                }
                
                context.startActivity(intent)
                
                // Shut down process to release bad state resources
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } catch (e: Exception) {
                Log.e(TAG, "Error in default crash handler rescue, falling back", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        Log.i(TAG, "Global Crash Interceptor successfully installed")
    }
}

/**
 * UI Error Boundary Composable protecting the application tree.
 */
@Composable
fun ErrorBoundary(
    modifier: Modifier = Modifier,
    fallback: @Composable (Throwable, onReset: () -> Unit) -> Unit = { exception, onReset ->
        val context = LocalContext.current
        val message = exception.localizedMessage ?: exception.message ?: "An unexpected error occurred"
        
        // Format stacktrace
        val sw = StringWriter()
        exception.printStackTrace(PrintWriter(sw))
        val stacktrace = sw.toString()

        CrashRecoveryScreen(
            errorMessage = message,
            errorStacktrace = stacktrace,
            onRestart = onReset,
            onClearCache = {
                try {
                    val sharedPrefs = context.getSharedPreferences("AudioCityPrefs", Context.MODE_PRIVATE)
                    sharedPrefs.edit().clear().commit()
                    
                    // Clear app cache directory
                    context.cacheDir.deleteRecursively()
                    Toast.makeText(context, "Cache and preferences cleared successfully", Toast.LENGTH_LONG).show()
                    onReset()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to clear app cache: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    },
    content: @Composable () -> Unit
) {
    var caughtError by remember { mutableStateOf<Throwable?>(null) }

    val scope = remember {
        object : ErrorBoundaryScope {
            override fun reportError(throwable: Throwable) {
                caughtError = throwable
            }
        }
    }

    CompositionLocalProvider(LocalErrorBoundary provides scope) {
        if (caughtError != null) {
            fallback(caughtError!!) {
                caughtError = null
            }
        } else {
            content()
        }
    }
}

/**
 * A beautiful, highly-polished Material 3 Crash Recovery Screen.
 * Fully styled in conformance with the "Elegant Dark" design theme.
 */
@Composable
fun CrashRecoveryScreen(
    errorMessage: String,
    errorStacktrace: String,
    onRestart: () -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showDiagnostics by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DeepMidnight
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Elegant Dark theme logo / warning emblem
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                RedFlag.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(40.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(GlowAmbient, RoundedCornerShape(16.dp))
                        .border(1.5.dp, RedFlag.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = "System Warning",
                        tint = RedFlag,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Subdued tag for premium system
            Box(
                modifier = Modifier
                    .background(RedFlag.copy(alpha = 0.15f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "RESONANCE INTERRUPTION DETECTED",
                    color = RedFlag,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Oops, Audio City experienced an interruption",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextLight,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "An unexpected exception disrupted the audio engine pipeline. You can safely attempt a hot reload, clear local configurations, or read diagnostics below.",
                fontSize = 13.sp,
                color = VocalLavender,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // The main card showing the error message summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateDark),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.BugReport,
                                contentDescription = "Error Info",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Incident Log Details",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextLight,
                                letterSpacing = 1.sp
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString("$errorMessage\n\n$errorStacktrace"))
                                Toast.makeText(context, "Diagnostic log copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy Error",
                                tint = MutedText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = errorMessage,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = RedFlag,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DeepMidnight, RoundedCornerShape(8.dp))
                            .border(0.5.dp, CardBorder, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Collapsible technical diagnostics container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDiagnostics = !showDiagnostics },
                colors = CardDefaults.cardColors(containerColor = SlateDark),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Technical Diagnostics Stacktrace",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = VocalLavender
                        )
                        Icon(
                            imageVector = if (showDiagnostics) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Toggle Stacktrace",
                            tint = MutedText
                        )
                    }

                    AnimatedVisibility(
                        visible = showDiagnostics,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 250.dp)
                                    .background(DeepMidnight, RoundedCornerShape(6.dp))
                                    .border(0.5.dp, CardBorder, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                val horizontalScrollState = rememberScrollState()
                                val verticalScrollState = rememberScrollState()
                                Text(
                                    text = errorStacktrace,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MutedText,
                                    lineHeight = 15.sp,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(verticalScrollState)
                                        .horizontalScroll(horizontalScrollState)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Actions panel with prominent premium recovery options
            Button(
                onClick = onRestart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = GlowAmbient
                ),
                shape = RoundedCornerShape(100.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Restart",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hot Relaunch App",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onClearCache,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = VocalLavender
                ),
                border = BorderStroke(1.2.dp, CardBorder),
                shape = RoundedCornerShape(100.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = "Clear Cache",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clear Cache & Reset Data",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
