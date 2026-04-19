package com.liujiaming.embypro

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LibraryItemsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = false)
        setContentView(R.layout.activity_library_items)
        supportActionBar?.hide()
    }

    companion object {
        const val EXTRA_LIBRARY_ID = "extra_library_id"
        const val EXTRA_LIBRARY_NAME = "extra_library_name"
        const val EXTRA_BASE_URL = "extra_base_url"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
    }
}
