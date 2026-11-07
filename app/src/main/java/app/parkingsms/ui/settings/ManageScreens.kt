package app.parkingsms.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.parkingsms.R
import app.parkingsms.container
import app.parkingsms.data.City
import app.parkingsms.data.Plate
import app.parkingsms.ui.PanelCard
import app.parkingsms.ui.PlateBadge
import app.parkingsms.ui.AppTopBar
import app.parkingsms.ui.CityEditDialog
import app.parkingsms.ui.ConfirmDialog
import app.parkingsms.ui.PlateEditDialog
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun ManageCitiesScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.container.repository
    val scope = rememberCoroutineScope()
    val cities by repo.cities.collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<City?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<City?>(null) }

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.settings_cities), onBack = onBack) },
        bottomBar = {
            Button(
                onClick = { adding = true },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(60.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.city_add_title), modifier = Modifier.padding(start = 8.dp))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (cities.isEmpty()) {
                item { Text(stringResource(R.string.cities_empty), style = MaterialTheme.typography.bodyLarge) }
            }
            items(items = cities, key = { it.id }) { city ->
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(city.name, style = MaterialTheme.typography.titleMedium)
                            Text(city.smsNumber, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { editing = city }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit))
                        }
                        IconButton(onClick = { deleting = city }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete))
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        CityEditDialog(initial = null, onDismiss = { adding = false }, onSave = { name, number ->
            adding = false
            scope.launch { repo.addCity(name, number) }
        })
    }
    val edit = editing
    if (edit != null) {
        CityEditDialog(initial = edit, onDismiss = { editing = null }, onSave = { name, number ->
            editing = null
            scope.launch { repo.updateCity(edit.copy(name = name, smsNumber = number)) }
        })
    }
    val del = deleting
    if (del != null) {
        ConfirmDialog(
            title = stringResource(R.string.city_delete_title),
            text = stringResource(R.string.city_delete_text, del.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                deleting = null
                scope.launch { repo.deleteCity(del.id) }
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
fun ManagePlatesScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.container.repository
    val scope = rememberCoroutineScope()
    val plates by repo.plates.collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<Plate?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Plate?>(null) }

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.settings_plates), onBack = onBack) },
        bottomBar = {
            Button(
                onClick = { adding = true },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(60.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.plate_add_title), modifier = Modifier.padding(start = 8.dp))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (plates.isEmpty()) {
                item { Text(stringResource(R.string.plates_empty), style = MaterialTheme.typography.bodyLarge) }
            }
            items(items = plates, key = { it.id }) { plate ->
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            PlateBadge(plate.plateText, large = false)
                            val label = plate.label
                            if (!label.isNullOrEmpty()) Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { editing = plate }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit))
                        }
                        IconButton(onClick = { deleting = plate }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete))
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        PlateEditDialog(initial = null, onDismiss = { adding = false }, onSave = { text, label ->
            adding = false
            scope.launch { repo.addPlate(text, label) }
        })
    }
    val edit = editing
    if (edit != null) {
        PlateEditDialog(initial = edit, onDismiss = { editing = null }, onSave = { text, label ->
            editing = null
            scope.launch { repo.updatePlate(edit.copy(plateText = text, label = label)) }
        })
    }
    val del = deleting
    if (del != null) {
        ConfirmDialog(
            title = stringResource(R.string.plate_delete_title),
            text = stringResource(R.string.plate_delete_text, del.plateText),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                deleting = null
                scope.launch { repo.deletePlate(del.id) }
            },
            onDismiss = { deleting = null }
        )
    }
}
