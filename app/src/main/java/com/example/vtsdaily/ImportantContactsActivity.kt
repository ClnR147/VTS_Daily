package com.example.vtsdaily

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.vtsdaily.storage.ContactListScreen
import com.example.vtsdaily.ui.theme.VTSDailyTheme // if you use your app theme

class ImportantContactsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VTSDailyTheme { // or remove if you don't use it
                ContactListScreen(this)
            }
        }
    }
}
