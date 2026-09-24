package com.emberr.data.local.prefs
object SyncConstants {
    const val KEY_ACTIVE_SPACE_ID = "active_space_id"
    // Sorting
    const val KEY_SORT_TYPE = "sort_type"
    const val KEY_SORT_ORDER = "sort_order"
    // Desktop State
    const val KEY_LAST_OPENED_STATE = "last_opened_desktop_state"
    // Home section collapse state
    const val KEY_HOME_SECTION_EXPANDED_PREFIX = "home_section_expanded_"
    const val HOME_SECTION_FAVORITES = "favorites"
    const val HOME_SECTION_NOTES = "notes"
    const val HOME_SECTION_RECENTS = "recents"
    const val DEFAULT_HOME_SECTION_EXPANDED = true
    // Calendar
    const val KEY_CALENDAR_VIEW_MODE = "calendar_view_mode"
    const val DEFAULT_CALENDAR_VIEW_MODE = "DAY"
    // Appearance
    const val KEY_THEME_PREFERENCE = "theme_preference"
    const val DEFAULT_THEME_PREFERENCE = "SYSTEM"
    const val KEY_FONT_SIZE_PREFERENCE = "font_size_preference"
    const val DEFAULT_FONT_SIZE_PREFERENCE = "DEFAULT"
    const val KEY_FONT_STYLE_PREFERENCE = "font_style_preference"
    const val DEFAULT_FONT_STYLE_PREFERENCE = "POPPINS"
    const val KEY_TOP_BAR_FADE_STYLE = "top_bar_fade_style"
    const val DEFAULT_TOP_BAR_FADE_STYLE = "BLUR"
    const val KEY_SUBNOTE_OPEN_MODE = "subnote_open_mode"
    const val DEFAULT_SUBNOTE_OPEN_MODE = "SIDE_PANEL"
    // AI generation
    const val KEY_AI_GENERATION_MODE = "ai_generation_mode"
    const val DEFAULT_AI_GENERATION_MODE = "LOCAL"
    const val KEY_SELECTED_EXTERNAL_AI_PROVIDER = "selected_external_ai_provider"
    const val DEFAULT_SELECTED_EXTERNAL_AI_PROVIDER = "OPENAI"
    const val KEY_KNOWLEDGE_MODE = "ai_knowledge_mode"
    const val DEFAULT_KNOWLEDGE_MODE = "DEFAULT"
    const val KEY_MAX_OUTPUT_TOKENS = "ai_max_output_tokens"
    const val DEFAULT_MAX_OUTPUT_TOKENS = 1024
    const val KEY_LOCAL_CONTEXT_LENGTH = "ai_local_context_length"
    const val DEFAULT_LOCAL_CONTEXT_LENGTH = 4096
    const val KEY_INSTALLED_LOCAL_MODELS_JSON = "ai_installed_local_models_json"
    const val DEFAULT_INSTALLED_LOCAL_MODELS_JSON = "[]"
    const val KEY_SELECTED_LOCAL_MODEL_FILE_NAME = "ai_selected_local_model_file_name"
    const val KEY_AI_FEATURES_DISABLED = "ai_features_disabled"
    const val DEFAULT_AI_FEATURES_DISABLED = false
    const val KEY_EXTERNAL_AI_READ_ONLY = "external_ai_read_only"
    const val DEFAULT_EXTERNAL_AI_READ_ONLY = false
    const val KEY_SHOW_SCROLLBAR = "show_scrollbar"
    const val DEFAULT_SHOW_SCROLLBAR = false
    const val KEY_CUSTOM_WINDOW_FRAME = "custom_window_frame"
    const val DEFAULT_CUSTOM_WINDOW_FRAME = true
    const val KEY_AUTO_HIDE_TITLE_BAR = "auto_hide_title_bar"
    const val DEFAULT_AUTO_HIDE_TITLE_BAR = true
    const val KEY_AUTOMATIC_UPDATE_CHECK = "automatic_update_check"
    const val DEFAULT_AUTOMATIC_UPDATE_CHECK = true
    const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    const val DEFAULT_ONBOARDING_COMPLETED = false
    const val KEY_SAMPLE_DAILY_NOTE_SEEDED = "sample_daily_note_seeded"
    const val DEFAULT_SAMPLE_DAILY_NOTE_SEEDED = false
    const val KEY_SAMPLE_NOTES_SEEDED = "sample_notes_seeded"
    const val DEFAULT_SAMPLE_NOTES_SEEDED = false
    const val KEY_MEDIA_REFERENCE_LIST_BUILT = "media_reference_list_built"
    const val DEFAULT_MEDIA_REFERENCE_LIST_BUILT = false
    // Sync Keys
    const val KEY_SYNC_TIMESTAMP = "last_sync_timestamp"
    const val KEY_SELF_HOST_SYNC_TIMESTAMP = "self_host_last_sync_timestamp"
    const val KEY_SELF_HOST_SUPPORTS_ETAGS = "self_host_supports_etags"
    const val KEY_SELF_HOST_MANIFEST_ETAG = "self_host_manifest_etag"
    const val KEY_SYNC_AUTH_TOKEN = "sync_auth_token"
    const val KEY_SYNC_IP_ADDRESS = "sync_ip_address"
    const val KEY_SYNC_PORT = "sync_port"
    const val KEY_SYNC_ENCRYPTION_KEY = "sync_encryption_key"
    const val KEY_SYNC_PAIRING_CONFIRMED = "sync_pairing_confirmed"
    // Defaults
    const val DEFAULT_PORT = 8080
    const val DEFAULT_SORT_TYPE = "LAST_EDITED"
    const val DEFAULT_SORT_ORDER = "DESCENDING"
    // API Routes
    const val ROUTE_FETCH = "/sync/fetch"
    const val ROUTE_PUSH = "/sync/push"
    const val ROUTE_UNPAIR = "/sync/unpair"
    // HMAC Auth
    const val HEADER_SYNC_TIMESTAMP = "X-Sync-Timestamp"
    const val HEADER_SYNC_SIGNATURE = "X-Sync-Signature"
    const val HEADER_SYNC_SCHEMA_VERSION = "X-Sync-Schema-Version"
    const val MAX_REQUEST_AGE_MS = 30_000L
    // Resumable media transfers
    const val HEADER_RESUME_OFFSET = "X-Resume-Offset"
    // Bookmarks
    const val KEY_BOOKMARK_CATEGORY_ORDER_JSON = "bookmark_category_order_json"
    const val DEFAULT_BOOKMARK_CATEGORY_ORDER_JSON = ""
    // Favorites
    const val KEY_FAVORITE_NOTE_ORDER_JSON = "favorite_note_order_json"
    const val DEFAULT_FAVORITE_NOTE_ORDER_JSON = ""
    // Folder tree
    const val KEY_EXPANDED_FOLDER_IDS_JSON = "expanded_folder_ids_json"
    const val DEFAULT_EXPANDED_FOLDER_IDS_JSON = ""
}