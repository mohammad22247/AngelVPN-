package com.angelvpn.app

import android.app.Application
import com.angelvpn.app.data.ProfileStore

class AngelApp : Application() {
    lateinit var profileStore: ProfileStore
        private set

    override fun onCreate() {
        super.onCreate()
        profileStore = ProfileStore(this)
    }
}
