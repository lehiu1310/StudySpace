package com.example.util

import android.content.Context
import android.content.res.Configuration
import com.example.R
import java.util.Locale

object StudyLocalization {
    private var appContext: Context? = null

    private val keyToResId = mapOf(
        "product_home" to R.string.product_home,
        "product_courses" to R.string.product_courses,
        "product_plan" to R.string.product_plan,
        "product_account" to R.string.product_account,
        "learning_title" to R.string.learning_title,
        "app_name" to R.string.app_name,
        "dashboard" to R.string.dashboard,
        "focus" to R.string.focus,
        "planner" to R.string.planner,
        "groups" to R.string.groups,
        "profile" to R.string.profile,
        "today_stats" to R.string.today_stats,
        "weekly_stats" to R.string.weekly_stats,
        "tasks_completed" to R.string.tasks_completed,
        "focus_sessions" to R.string.focus_sessions,
        "reward_points" to R.string.reward_points,
        "level" to R.string.level,
        "claim_rewards" to R.string.claim_rewards,
        "ai_exercise" to R.string.ai_exercise,
        "generate_ai" to R.string.generate_ai,
        "ai_loading" to R.string.ai_loading,
        "start" to R.string.start,
        "pause" to R.string.pause,
        "reset" to R.string.reset,
        "focus_mode" to R.string.focus_mode,
        "break_mode" to R.string.break_mode,
        "meditation_mode" to R.string.meditation_mode,
        "add_task" to R.string.add_task,
        "task_name" to R.string.task_name,
        "category" to R.string.category,
        "duration" to R.string.duration,
        "save" to R.string.save,
        "delete" to R.string.delete,
        "complete" to R.string.complete,
        "export_data" to R.string.export_data,
        "cloud_sync" to R.string.cloud_sync,
        "security_high" to R.string.security_high,
        "two_factor" to R.string.two_factor,
        "biometric" to R.string.biometric,
        "language_select" to R.string.language_select,
        "dark_mode" to R.string.dark_mode,
        "restore_factory" to R.string.restore_factory,
        "meditation_title" to R.string.meditation_title,
        "meditation_desc" to R.string.meditation_desc,
        "join_group" to R.string.join_group,
        "leave_group" to R.string.leave_group,
        "group_members" to R.string.group_members,
        "status_studying" to R.string.status_studying,
        "status_meditating" to R.string.status_meditating,
        "status_resting" to R.string.status_resting,
        "syncing" to R.string.syncing,
        "no_tasks" to R.string.no_tasks,
        "personalized_tip" to R.string.personalized_tip,
        "personalized_ex" to R.string.personalized_ex,
        "submit_challenge" to R.string.submit_challenge,
        "enter_answer" to R.string.enter_answer
    )

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun translate(key: String, lang: String): String {
        val context = appContext ?: return key
        val resId = keyToResId[key] ?: return key
        val locale = Locale.forLanguageTag(if (lang == "en") "en" else "vi")
        val config = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(config).resources.getString(resId)
    }
}
