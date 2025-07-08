package com.example.vtsdaily.ui.contacts

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.vtsdaily.model.ContactEntry
import com.example.vtsdaily.ui.ContactDialog

@Composable
fun FakeContactListScreen() {
    val context = LocalContext.current
    val fakeContacts = listOf(
        ContactEntry(name = "Dispatcher", phoneNumber = "805-555-1111"),
        ContactEntry(name = "Office", phoneNumber = "805-555-2222"),
        ContactEntry(name = "Mechanic", phoneNumber = "805-555-3333")
    )

    // Reuse the real ContactListScreen layout with dummy data
    ContactListScreenPreview(fakeContacts, context)
}

@Composable
private fun ContactListScreenPreview(contacts: List<ContactEntry>, context: Context) {
    androidx.compose.foundation.layout.Column {
        contacts.forEach {
            androidx.compose.material3.Text("${it.name} - ${it.phoneNumber}")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewContactsScreen() {
    FakeContactListScreen()
}
