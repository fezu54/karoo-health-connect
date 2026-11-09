package org.happycode.karoo.health_connect.frameworks.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.happycode.karoo.health_connect.domain.model.SyncError
import org.happycode.karoo.health_connect.domain.model.SyncResult
import org.happycode.karoo.health_connect.frameworks.KarooApplication

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = (applicationContext as? KarooApplication) ?: return Result.failure()
        val syncRidesUseCase = app.appContainer.syncRidesUseCase

        return when (val result = syncRidesUseCase.execute()) {
            is SyncResult.Success -> Result.success()
            is SyncResult.Failure -> when (result.error) {
                is SyncError.NetworkError -> Result.retry()
                else -> Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "KarooPeriodicSyncWorker"
    }
}
