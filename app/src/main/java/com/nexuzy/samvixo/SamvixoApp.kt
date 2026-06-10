package com.nexuzy.samvixo

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.nexuzy.samvixo.util.PresenceManager

class SamvixoApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Firebase
        FirebaseApp.initializeApp(this)

        // App Check — Play Integrity
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        // AdMob
        MobileAds.initialize(this)

        // Online presence
        PresenceManager().updatePresence()
    }
}
