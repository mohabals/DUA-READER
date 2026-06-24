package com.example.api

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast

object GeminiClient {
    private const val TAG = "GeminiClient"

    fun launchOnDeviceAnalysis(context: Context, sentenceText: String) {
        val prompt = """
            Explain this sentence.
            Include:
            Natural translation
            Word breakdown
            Grammar notes
            Common usage
            Alternative expressions
            
            Sentence: "$sentenceText"
        """.trimIndent()

        try {
            // 1. Copy the formatted prompt to the Clipboard so the user has it ready
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Gemini Russian Analysis", prompt)
            clipboard.setPrimaryClip(clip)

            // 2. Build standard Plain Text Share Intent to hand content to Gemini
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, prompt)
                type = "text/plain"
            }

            val packageManager = context.packageManager
            val resolveInfos = packageManager.queryIntentActivities(sendIntent, 0)
            var launchedDirectly = false

            // Attempt to locate Gemini App or Google Assistant directly for direct launch
            for (info in resolveInfos) {
                val pkgName = info.activityInfo.packageName
                if (pkgName.contains("com.google.android.apps.bard") || 
                    pkgName.contains("com.google.android.apps.googleassistant")
                ) {
                    sendIntent.setPackage(pkgName)
                    context.startActivity(sendIntent)
                    launchedDirectly = true
                    break
                }
            }

            if (launchedDirectly) {
                Toast.makeText(context, "Prompt copied! Opening Gemini Assistant...", Toast.LENGTH_SHORT).show()
            } else {
                // If not found, use a system-styled share chooser so they can select Gemini or Google
                val chooser = Intent.createChooser(sendIntent, "Analyze Sentence with Gemini")
                // Flags to start in new task from Compose standard LocalContext if required
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            // Ultimate fallback to standard System ACTION_ASSIST overlay
            try {
                Toast.makeText(context, "Prompt copied! Sourcing Assistant...", Toast.LENGTH_SHORT).show()
                val assistIntent = Intent(Intent.ACTION_ASSIST).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(assistIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Please open Gemini manually and paste the copied prompt.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
