package com.mbk.loapunto

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        EntryStore.init(this)
    }
}
