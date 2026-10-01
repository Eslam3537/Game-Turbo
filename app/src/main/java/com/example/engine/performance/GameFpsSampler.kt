package com.example.engine.performance

import android.content.Context

/**
 * Unified GameFpsSampler forwarding directly to SurfaceFlingerFpsEngine (Fix A2).
 */
object GameFpsSampler {
    suspend fun sampleGameFps(context: Context, gamePackage: String): Int? {
        return SurfaceFlingerFpsEngine.sampleFps(context, gamePackage)
    }
}
