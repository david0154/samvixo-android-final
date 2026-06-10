package com.nexuzy.samvixo.util

import android.content.Context
import com.google.mlkit.nl.entityextraction.*

class SpamDetector(private val context: Context) {
    
    private val entityExtractor = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH)
            .build()
    )

    fun analyzeMessage(text: String, callback: (SpamResult) -> Unit) {
        val params = EntityExtractionParams.Builder(text).build()
        
        // High-risk keywords for manual filter
        val scamKeywords = listOf("win", "prize", "lottery", "urgent", "bank", "account", "suspended", "click here")
        val containsScamKeyword = scamKeywords.any { text.contains(it, ignoreCase = true) }

        entityExtractor.annotate(params)
            .addOnSuccessListener { entities ->
                val riskyLinks = mutableListOf<String>()
                val containsRiskyEntity = entities.any { it.entities.any { entity -> 
                    val isRisky = entity.type == Entity.TYPE_URL || 
                                 entity.type == Entity.TYPE_IBAN || 
                                 entity.type == Entity.TYPE_PAYMENT_CARD
                    if (isRisky && entity.type == Entity.TYPE_URL) {
                        riskyLinks.add(it.annotatedText)
                    }
                    isRisky
                }}

                callback(SpamResult(
                    isSpam = containsRiskyEntity || containsScamKeyword,
                    riskyLinks = riskyLinks,
                    trustLevel = if (containsRiskyEntity) 0 else if (containsScamKeyword) 20 else 100
                ))
            }
            .addOnFailureListener {
                callback(SpamResult(isSpam = containsScamKeyword, trustLevel = 50))
            }
    }
}

data class SpamResult(
    val isSpam: Boolean,
    val riskyLinks: List<String> = emptyList(),
    val trustLevel: Int
)
