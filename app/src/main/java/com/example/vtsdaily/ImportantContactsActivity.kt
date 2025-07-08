package com.example.vtsdaily

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.vtsdaily.ui.ContactListScreen
import com.example.vtsdaily.ui.theme.VTSDailyTheme

class ImportantContactsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VTSDailyTheme {
                ContactListScreen(this)
            }
        }
    }
}

