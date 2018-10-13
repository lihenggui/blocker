package com.merxury.blocker.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class ScheduledWork(context: Context, workerParameters: WorkerParameters) : Worker(context, workerParameters) {
    override fun doWork(): Result {
        return Result.SUCCESS
    }

}