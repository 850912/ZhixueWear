package org.openwear.temperature

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log

/**
 * 后台自动测量任务。
 *
 * 由 `JobScheduler` 周期唤醒，每次只做一次采样：
 * 连接三星健康服务 -> 拿到一个有效样本 -> 断开 -> 写入历史。
 *
 * 任务跑在一次性创建的线程上，结束后线程随之退出，进程可以被系统回收，
 * 不会留下常驻线程或常驻服务。
 */
class BackgroundMeasureJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val worker = Thread({
            var needsRetry = false
            try {
                needsRetry = BackgroundMeasurement.runOnce(applicationContext)
            } catch (error: Throwable) {
                Log.w(TAG, "后台自动测量失败", error)
            } finally {
                jobFinished(params, needsRetry)
            }
        }, "skin-temperature-job")
        worker.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    private companion object {
        const val TAG = "SkinTempJob"
    }
}
