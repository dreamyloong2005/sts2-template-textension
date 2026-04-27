package org.godotengine.godot

@Suppress("ClassName", "PropertyName")
class R private constructor() {
    class dimen private constructor() {
        companion object {
            @JvmField val button_height: Int = android.R.dimen.thumbnail_height
            @JvmField val button_padding: Int = android.R.dimen.thumbnail_width
            @JvmField val dialog_padding_horizontal: Int = android.R.dimen.dialog_min_width_major
            @JvmField val dialog_padding_vertical: Int = android.R.dimen.dialog_min_width_minor
            @JvmField val snackbar_bottom_margin: Int = android.R.dimen.thumbnail_width
            @JvmField val text_edit_height: Int = android.R.dimen.thumbnail_height
        }
    }

    class id private constructor() {
        companion object {
            @JvmField val approveCellular: Int = 0
            @JvmField val downloaderDashboard: Int = 0
            @JvmField val godot_fragment_container: Int = 0
            @JvmField val pauseButton: Int = 0
            @JvmField val progressAsFraction: Int = 0
            @JvmField val progressAsPercentage: Int = 0
            @JvmField val progressAverageSpeed: Int = 0
            @JvmField val progressBar: Int = 0
            @JvmField val progressTimeRemaining: Int = 0
            @JvmField val remote_godot_window_surface: Int = 0
            @JvmField val snackbar_action: Int = 0
            @JvmField val snackbar_text: Int = 0
            @JvmField val statusText: Int = 0
        }
    }

    class layout private constructor() {
        companion object {
            @JvmField val downloading_expansion: Int = 0
            @JvmField val godot_app_layout: Int = 0
            @JvmField val remote_godot_fragment_layout: Int = 0
            @JvmField val snackbar: Int = 0
        }
    }

    class string private constructor() {
        companion object {
            @JvmField val dialog_ok: Int = android.R.string.ok
            @JvmField val error_engine_setup_message: Int = android.R.string.dialog_alert_title
            @JvmField val error_missing_vulkan_requirements_message: Int = android.R.string.VideoView_error_text_unknown
            @JvmField val kilobytes_per_second: Int = android.R.string.unknownName
            @JvmField val state_completed: Int = android.R.string.ok
            @JvmField val state_connecting: Int = android.R.string.unknownName
            @JvmField val state_downloading: Int = android.R.string.unknownName
            @JvmField val state_failed_cancelled: Int = android.R.string.cancel
            @JvmField val state_failed_fetching_url: Int = android.R.string.dialog_alert_title
            @JvmField val state_failed_sdcard_full: Int = android.R.string.dialog_alert_title
            @JvmField val state_failed_unlicensed: Int = android.R.string.dialog_alert_title
            @JvmField val state_fetching_url: Int = android.R.string.unknownName
            @JvmField val state_idle: Int = android.R.string.unknownName
            @JvmField val state_paused_by_request: Int = android.R.string.cancel
            @JvmField val state_paused_network_setup_failure: Int = android.R.string.dialog_alert_title
            @JvmField val state_paused_network_unavailable: Int = android.R.string.dialog_alert_title
            @JvmField val state_paused_roaming: Int = android.R.string.dialog_alert_title
            @JvmField val state_paused_sdcard_unavailable: Int = android.R.string.dialog_alert_title
            @JvmField val state_paused_wifi_disabled: Int = android.R.string.dialog_alert_title
            @JvmField val state_paused_wifi_unavailable: Int = android.R.string.dialog_alert_title
            @JvmField val state_unknown: Int = android.R.string.unknownName
            @JvmField val text_button_pause: Int = android.R.string.cancel
            @JvmField val text_button_resume: Int = android.R.string.ok
            @JvmField val text_error_title: Int = android.R.string.dialog_alert_title
            @JvmField val time_remaining: Int = android.R.string.unknownName
            @JvmField val time_remaining_notification: Int = android.R.string.unknownName
        }
    }
}
