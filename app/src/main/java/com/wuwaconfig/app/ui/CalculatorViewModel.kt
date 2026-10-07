package com.wuwaconfig.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuwaconfig.app.config.MaterialCalculator
import com.wuwaconfig.app.config.MaterialData
import com.wuwaconfig.app.model.CalculatorCharacter
import com.wuwaconfig.app.model.CalculatorResult
import com.wuwaconfig.app.model.CalculatorWeapon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {
    private val _characters = MutableStateFlow<List<CalculatorCharacter>>(emptyList())
    val characters: StateFlow<List<CalculatorCharacter>> = _characters.asStateFlow()

    private val _weapons = MutableStateFlow<List<CalculatorWeapon>>(emptyList())
    val weapons: StateFlow<List<CalculatorWeapon>> = _weapons.asStateFlow()

    private val _selectedCharacter = MutableStateFlow<CalculatorCharacter?>(null)
    val selectedCharacter: StateFlow<CalculatorCharacter?> = _selectedCharacter.asStateFlow()

    private val _selectedWeapon = MutableStateFlow<CalculatorWeapon?>(null)
    val selectedWeapon: StateFlow<CalculatorWeapon?> = _selectedWeapon.asStateFlow()

    private val _characterResult = MutableStateFlow<CalculatorResult?>(null)
    val characterResult: StateFlow<CalculatorResult?> = _characterResult.asStateFlow()

    private val _weaponResult = MutableStateFlow<CalculatorResult?>(null)
    val weaponResult: StateFlow<CalculatorResult?> = _weaponResult.asStateFlow()

    /**
     * MaterialData is loaded in `WuWaConfigApp.onCreate`, so this only sorts two
     * maps. It still hops to IO because the caller is a composable's
     * LaunchedEffect and the sort of ~170 entries is not worth a main-thread stall
     * on a cold start.
     */
    fun loadLists() {
        viewModelScope.launch {
            val data = MaterialData.get()
            val (chars, weps) =
                withContext(Dispatchers.Default) {
                    data.characters.values.sortedBy { it.name } to data.weapons.values.sortedBy { it.name }
                }
            _characters.value = chars
            _weapons.value = weps
        }
    }

    fun selectCharacter(entry: CalculatorCharacter?) {
        _selectedCharacter.value = entry
        _characterResult.value = null
    }

    fun selectWeapon(entry: CalculatorWeapon?) {
        _selectedWeapon.value = entry
        _weaponResult.value = null
    }

    /**
     * Returns false when the requested range buys nothing, so the screen can say so
     * instead of rendering an empty results card that reads like a failure.
     */
    fun calculateCharacter(
        fromPhase: Int,
        toPhase: Int,
        skillFrom: Int,
        skillTo: Int,
    ): Boolean {
        val entry = _selectedCharacter.value ?: return false
        val result =
            MaterialCalculator.buildCharacterMaterials(
                characterName = entry.name,
                fromPhase = fromPhase,
                toPhase = toPhase,
                skillFrom = skillFrom,
                skillTo = skillTo,
            )
        _characterResult.value = result
        return result.totalItems > 0
    }

    fun calculateWeapon(
        fromPhase: Int,
        toPhase: Int,
    ): Boolean {
        val entry = _selectedWeapon.value ?: return false
        val result =
            MaterialCalculator.buildWeaponMaterials(
                weaponName = entry.name,
                fromPhase = fromPhase,
                toPhase = toPhase,
            )
        _weaponResult.value = result
        return result.totalItems > 0
    }
}
