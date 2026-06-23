package com.example.buylist

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.buylist.ui.theme.BuyListTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val LIST_PREFS = "buy_list_storage"
private const val LIST_KEY = "buy_list_items"
private val Context.dataStore by preferencesDataStore(name = "theme_settings")
private val DarkThemeKey = booleanPreferencesKey("dark_theme_enabled")

data class ShoppingItem(
    val title: String,
    val isBought: Boolean = false,
)

class BuyListViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(LIST_PREFS, Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(loadItems())
    val items: StateFlow<List<ShoppingItem>> = _items.asStateFlow()

    val isDarkTheme = application.dataStore.data
        .map { preferences -> preferences[DarkThemeKey] ?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun addItem(rawTitle: String) {
        val title = rawTitle.trim()
        if (title.isEmpty()) return

        _items.value = _items.value + ShoppingItem(title = title)
        saveItems()
    }

    fun toggleBought(index: Int) {
        _items.value = _items.value.mapIndexed { itemIndex, item ->
            if (itemIndex == index) item.copy(isBought = !item.isBought) else item
        }
        saveItems()
    }

    fun deleteItem(index: Int) {
        _items.value = _items.value.filterIndexed { itemIndex, _ -> itemIndex != index }
        saveItems()
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { preferences ->
                preferences[DarkThemeKey] = enabled
            }
        }
    }

    private fun saveItems() {
        val serialized = _items.value.joinToString(separator = "|||") { item ->
            val safeTitle = item.title
                .replace("\\", "\\\\")
                .replace("|", "\\|")
            "${item.isBought}::${safeTitle}"
        }
        prefs.edit().putString(LIST_KEY, serialized).apply()
    }

    private fun loadItems(): List<ShoppingItem> {
        val saved = prefs.getString(LIST_KEY, null).orEmpty()
        if (saved.isBlank()) return emptyList()

        return saved.split("|||").mapNotNull { chunk ->
            val dividerIndex = chunk.indexOf("::")
            if (dividerIndex <= 0) return@mapNotNull null

            val isBought = chunk.substring(0, dividerIndex).toBoolean()
            val title = chunk.substring(dividerIndex + 2)
                .replace("\\|", "|")
                .replace("\\\\", "\\")

            if (title.isBlank()) null else ShoppingItem(title = title, isBought = isBought)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val buyListViewModel: BuyListViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )
            val items by buyListViewModel.items.collectAsState()
            val isDarkTheme by buyListViewModel.isDarkTheme.collectAsState()

            BuyListTheme(darkTheme = isDarkTheme) {
                BuyListScreen(
                    items = items,
                    isDarkTheme = isDarkTheme,
                    onAddItem = buyListViewModel::addItem,
                    onToggleItem = buyListViewModel::toggleBought,
                    onDeleteItem = buyListViewModel::deleteItem,
                    onThemeToggle = buyListViewModel::setDarkTheme,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuyListScreen(
    items: List<ShoppingItem>,
    isDarkTheme: Boolean,
    onAddItem: (String) -> Unit,
    onToggleItem: (Int) -> Unit,
    onDeleteItem: (Int) -> Unit,
    onThemeToggle: (Boolean) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val boughtCount = items.count { it.isBought }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Список покупок",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Темная тема")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onThemeToggle,
                    )
                }
            }

            Text(text = "Всего: ${items.size}")
            Text(text = "Куплено: $boughtCount")

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Добавить товар") },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onAddItem(text)
                        text = ""
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Добавить",
                    )
                }
            }

            if (items.isEmpty()) {
                Text(text = "Список пуст")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(items) { index, item ->
                        ShoppingCard(
                            item = item,
                            onToggle = { onToggleItem(index) },
                            onDelete = { onDeleteItem(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingCard(
    item: ShoppingItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onToggle,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Куплено",
                        tint = if (item.isBought) MaterialTheme.colorScheme.primary else Color.Gray,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = item.title,
                        textDecoration = if (item.isBought) TextDecoration.LineThrough else null,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (item.isBought) "Куплено" else "Не куплено",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }

            TextButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Удалить",
                )
            }
        }
    }
}
