package com.kyled.receiptscanner

import android.app.Application
import com.kyled.receiptscanner.data.AuthRepository
import com.kyled.receiptscanner.data.ReceiptRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json

class ReceiptApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** App-wide singletons. Small enough that a DI framework isn't worth it. */
class AppContainer(app: Application) {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        defaultSerializer = KotlinXSerializer(json)
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Functions)
    }

    val authRepository = AuthRepository(supabase)
    val receiptRepository = ReceiptRepository(supabase, json, app.cacheDir)
}
