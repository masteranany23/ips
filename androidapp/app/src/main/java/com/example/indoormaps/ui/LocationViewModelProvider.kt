package com.example.indoormaps.ui

import android.app.Application

/**
 * Singleton provider to ensure single LocationViewModel instance
 * is shared across Home and Map screens for continuous background scanning
 */
object LocationViewModelProvider {
    
    @Volatile
    private var instance: LocationViewModel? = null
    
    fun getInstance(application: Application): LocationViewModel {
        return instance ?: synchronized(this) {
            instance ?: LocationViewModel(application).also { instance = it }
        }
    }
}
