package com.herbertindustries.alex311

import android.app.Application
import com.herbertindustries.alex311.data.*
import com.herbertindustries.alex311.background.StatusWorker

class Alex311Application: Application() {
    val secure by lazy { SecureStore(this) }
    val api by lazy {
        val settings = getSharedPreferences("settings", MODE_PRIVATE)
        Api(if (BuildConfig.DEBUG) settings.getString("server", Api.PRODUCTION) ?: Api.PRODUCTION else Api.PRODUCTION)
    }
    val session by lazy { Session(this, api, secure) }
    override fun onCreate() {
        super.onCreate()
        session
        StatusWorker.createChannel(this)
        if (session.signedIn.value) StatusWorker.schedule(this)
    }
}
