package org.openwear.temperature

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

/**
 * 后台自动测量的调度器。
 *
 * 为什么用 `JobScheduler` 而不是常驻前台服务或精确闹钟：
 *  - **唤醒次数最少**：周期任务由系统统一编排，自动适配 Doze / App Standby 桶，
 *    系统会把多个任务合并到同一个唤醒窗口；
 *  - **不常驻**：没有常驻服务、没有前台通知、没有 wakelock，任务结束进程即可被回收；
 *  - **不打扰精确闹钟配额**：不使用 `setExact*`，不需要 `SCHEDULE_EXACT_ALARM` 特权；
 *  - **低电量自动让路**：`setRequiresBatteryNotLow(true)` 让系统在低电量时推迟任务；
 *  - **重启后依然有效**：`setPersisted(true)`，设备重启后任务自动恢复（需要
 *    `RECEIVE_BOOT_COMPLETED` 权限）。
 *
 * 单次任务的耗电主要来自皮肤温度传感器本身：任务里只取 **一个** 样本就断开连接，
 * 传感器处于唤醒状态的时间被压缩到十几秒。
 */
internal object BackgroundMeasureScheduler {
    /** 任务 ID（任意正整数即可，这里取一个便于识别的值）。 */
    private const val JOB_ID = 0x5701

    /** 开启或关闭后台自动测量。 */
    fun setEnabled(context: Context, enabled: Boolean, interval: BackgroundInterval) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            ?: return
        if (!enabled) {
            scheduler.cancel(JOB_ID)
            return
        }
        val component = ComponentName(context, BackgroundMeasureJobService::class.java)
        val periodMillis = interval.minutes * 60_000L
        val flexMillis = (periodMillis / 4).coerceAtLeast(MIN_FLEX_MILLIS)
        val info = JobInfo.Builder(JOB_ID, component)
            .setPeriodic(periodMillis)
            .setFlexMillis(flexMillis)
            .setRequiresBatteryNotLow(true)
            .setPersisted(true)
            .setBackoffCriteria(BACKOFF_MILLIS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        scheduler.schedule(info)
    }

    /** 当前是否已经排期了后台测量任务。 */
    fun isScheduled(context: Context): Boolean {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            ?: return false
        return runCatching { scheduler.allPendingJobs.any { it.id == JOB_ID } }.getOrDefault(false)
    }

    private const val MIN_FLEX_MILLIS = 5 * 60_000L
    private const val BACKOFF_MILLIS = 30_000L
}
