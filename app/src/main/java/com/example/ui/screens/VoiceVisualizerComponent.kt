package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.sin

@Composable
fun VoiceVisualizerComponent(
    amplitude: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    statusText: String = "Listening to your voice..."
) {
    // Phase transition for flowing wave animation
    val infiniteTransition = rememberInfiniteTransition(label = "voice_wave_phase")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Bouncing/scaling ambient glow size
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SlateDark.copy(alpha = 0.85f))
            .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Main Multi-Wave Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            ) {
                val width = size.width
                val height = size.height
                val centerY = height / 2f

                // Ambient Radial Gradient behind waves
                if (isActive) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                NeonCyan.copy(alpha = 0.18f * (amplitude + 0.5f)),
                                AccentPurple.copy(alpha = 0.08f * (amplitude + 0.5f)),
                                Color.Transparent
                            )
                        ),
                        radius = (width * 0.45f) * glowPulse,
                        center = Offset(width / 2f, centerY)
                    )
                }

                // Draw 3 dynamic bezier waves with different properties
                val waveConfigs = listOf(
                    WaveConfig(
                        color = NeonCyan,
                        alpha = 0.85f,
                        frequency = 1.3f,
                        amplitudeFactor = 0.85f,
                        strokeWidth = 2.5f,
                        phaseOffset = 0f
                    ),
                    WaveConfig(
                        color = AccentPurple,
                        alpha = 0.65f,
                        frequency = 1.8f,
                        amplitudeFactor = 0.65f,
                        strokeWidth = 1.8f,
                        phaseOffset = (Math.PI / 3f).toFloat()
                    ),
                    WaveConfig(
                        color = VocalLavender,
                        alpha = 0.5f,
                        frequency = 2.4f,
                        amplitudeFactor = 0.45f,
                        strokeWidth = 1.2f,
                        phaseOffset = (2 * Math.PI / 3f).toFloat()
                    )
                )

                waveConfigs.forEach { config ->
                    val path = Path()
                    val points = 120
                    val step = width / points

                    for (i in 0..points) {
                        val x = i * step
                        // Apply a hanning-like window function to taper the waves nicely at the left and right edges
                        val window = sin((i.toFloat() / points) * Math.PI).toFloat()

                        // Calculate sinusoidal elevation scaled by incoming amplitude
                        val currentAmp = if (isActive) {
                            (centerY * 0.75f * config.amplitudeFactor) * (amplitude + 0.12f) * window
                        } else {
                            (centerY * 0.08f) * window // quiet state
                        }

                        val angle = (i.toFloat() / points) * (2 * Math.PI) * config.frequency + phaseShift + config.phaseOffset
                        val y = centerY + sin(angle).toFloat() * currentAmp

                        if (i == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = config.color.copy(alpha = config.alpha),
                        style = Stroke(width = config.strokeWidth.dp.toPx())
                    )
                }

                // Add small glowing dots overlay reacting to mic input
                if (isActive && amplitude > 0.15f) {
                    val dotCount = 5
                    for (d in 0 until dotCount) {
                        val fraction = (d + 1).toFloat() / (dotCount + 1)
                        val x = width * fraction
                        val window = sin(fraction * Math.PI).toFloat()
                        val dotY = centerY + sin(phaseShift * 2 + d).toFloat() * (centerY * 0.4f * amplitude * window)
                        drawCircle(
                            color = NeonCyan.copy(alpha = 0.7f),
                            radius = 2.5f.dp.toPx() * (amplitude + 0.5f),
                            center = Offset(x, dotY)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-status caption with dynamic styling
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (isActive) NeonCyan else Color.Gray, RoundedCornerShape(50))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    color = if (isActive) NeonCyan else MutedText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

private data class WaveConfig(
    val color: Color,
    val alpha: Float,
    val frequency: Float,
    val amplitudeFactor: Float,
    val strokeWidth: Float,
    val phaseOffset: Float
)
