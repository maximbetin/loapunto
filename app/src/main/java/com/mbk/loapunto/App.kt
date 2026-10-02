package com.mbk.loapunto

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        EntryStore.init(this)
        Reminders.createChannels(this)
        Reminders.scheduleAll(this, EntryStore.entries.value)
        EntryStore.onChange = { old, new -> Reminders.sync(this, old, new) }
    }
}
