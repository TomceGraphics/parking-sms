package app.parkingsms.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.parkingsms.AppContainer
import app.parkingsms.R
import app.parkingsms.container
import app.parkingsms.data.Plate
import app.parkingsms.ui.BigButton
import app.parkingsms.ui.HeaderPanel
import app.parkingsms.ui.LogoMark
import app.parkingsms.ui.PanelCard
import app.parkingsms.ui.PlateBadge
import app.parkingsms.ui.SectionLabel
import app.parkingsms.ui.permissions.PermissionChecklist
import app.parkingsms.util.TextCleaner
import kotlinx.coroutines.launch

class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    private var plateId: Long? = null
    private var cityId: Long? = null

    fun savePlate(text: String) {
        viewModelScope.launch {
            val id = plateId
            if (id == null) {
                plateId = c.repository.addPlate(text, null)
            } else {
                c.repository.updatePlate(Plate(id = id, plateText = text))
            }
        }
    }

    fun saveCity(name: String, number: String) {
        viewModelScope.launch {
            val id = cityId
            if (id == null) {
                cityId = c.repository.addCity(name, number)
            } else {
                c.repository.updateCity(app.parkingsms.data.City(id = id, name = name, smsNumber = number))
            }
        }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            plateId?.let { c.settingsStore.setLastPlate(it) }
            cityId?.let { c.settingsStore.setLastCity(it) }
            c.settingsStore.setOnboardingDone(true)
            onDone()
        }
    }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { OnboardingViewModel(c) }
        }
    }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val container = LocalContext.current.container
    val vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(container))
    var step by rememberSaveable { mutableIntStateOf(0) }
    var plate by rememberSaveable { mutableStateOf("") }
    var cityName by rememberSaveable { mutableStateOf("") }
    var cityNumber by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = step > 0) { step -= 1 }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HeaderPanel {
                Row(modifier = Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    LogoMark(size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    for (i in 0 until 3) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.onboarding_step, step + 1, 3),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (step) {
                0 -> {
                    Text(stringResource(R.string.onboarding_plate_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.onboarding_plate_desc),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PanelCard(modifier = Modifier.fillMaxWidth()) {
                        SectionLabel(stringResource(R.string.label_plate))
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = plate,
                            onValueChange = { plate = TextCleaner.cleanPlate(it) },
                            textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 3.sp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        PlateBadge(plate)
                    }
                    BigButton(
                        label = stringResource(R.string.action_next),
                        onClick = {
                            vm.savePlate(plate)
                            step = 1
                        },
                        enabled = plate.isNotEmpty(),
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary
                    )
                }
                1 -> {
                    Text(stringResource(R.string.onboarding_city_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.onboarding_city_desc),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PanelCard(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = cityName,
                            onValueChange = { cityName = it },
                            label = { Text(stringResource(R.string.label_city_name)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = cityNumber,
                            onValueChange = { cityNumber = TextCleaner.cleanSmsNumber(it) },
                            label = { Text(stringResource(R.string.label_sms_number)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    BigButton(
                        label = stringResource(R.string.action_next),
                        onClick = {
                            vm.saveCity(cityName.trim(), cityNumber)
                            step = 2
                        },
                        enabled = cityName.isNotBlank() && cityNumber.isNotEmpty(),
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary
                    )
                }
                else -> {
                    Text(stringResource(R.string.onboarding_perm_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.onboarding_perm_desc),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PermissionChecklist()
                    BigButton(
                        label = stringResource(R.string.action_finish),
                        onClick = { vm.finish(onFinished) },
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
