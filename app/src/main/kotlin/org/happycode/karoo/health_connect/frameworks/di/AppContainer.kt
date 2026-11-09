package org.happycode.karoo.health_connect.frameworks.di

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import org.happycode.karoo.health_connect.BuildConfig
import org.happycode.karoo.health_connect.adapters.fit.GarminFitParserAdapter
import org.happycode.karoo.health_connect.adapters.healthconnect.AndroidHealthConnectGateway
import org.happycode.karoo.health_connect.adapters.sram.SharedPreferencesTokenStorage
import org.happycode.karoo.health_connect.adapters.sram.SramRideSourceAdapter
import org.happycode.karoo.health_connect.adapters.sram.TokenStorage
import org.happycode.karoo.health_connect.adapters.storage.SharedPreferencesSyncRepository
import org.happycode.karoo.health_connect.application.SyncRidesUseCase
import org.happycode.karoo.health_connect.domain.gateway.FitParser
import org.happycode.karoo.health_connect.domain.gateway.HealthConnectGateway
import org.happycode.karoo.health_connect.domain.gateway.RideSourceGateway
import org.happycode.karoo.health_connect.domain.gateway.SyncPreferencesRepository

class AppContainer(private val context: Context) {

    private val sharedPreferences by lazy {
        context.getSharedPreferences("karoo_health_connect_prefs", Context.MODE_PRIVATE)
    }

    val tokenStorage: TokenStorage by lazy {
        SharedPreferencesTokenStorage(sharedPreferences)
    }

    val preferencesRepository: SyncPreferencesRepository by lazy {
        SharedPreferencesSyncRepository(sharedPreferences)
    }

    val rideSourceGateway: RideSourceGateway by lazy {
        val proxyUrl = BuildConfig.SRAM_TOKEN_PROXY_URL.takeIf { it.isNotBlank() }
        if (proxyUrl != null) {
            SramRideSourceAdapter(
                tokenStorage = tokenStorage,
                tokenUrl = proxyUrl,
            )
        } else {
            SramRideSourceAdapter(tokenStorage = tokenStorage)
        }
    }

    val fitParser: FitParser by lazy {
        GarminFitParserAdapter()
    }

    val healthConnectGateway: HealthConnectGateway by lazy {
        AndroidHealthConnectGateway(
            healthConnectClient = HealthConnectClient.getOrCreate(context),
        ) { HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE }
    }

    val syncRidesUseCase: SyncRidesUseCase by lazy {
        SyncRidesUseCase(
            rideSourceGateway,
            fitParser,
            healthConnectGateway,
            preferencesRepository,
        )
    }
}
