package com.wuwaconfig.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuwaconfig.app.config.CharacterBuild
import com.wuwaconfig.app.config.ChosenPlayer
import com.wuwaconfig.app.config.KuroClient
import com.wuwaconfig.app.config.KuroGuide
import com.wuwaconfig.app.config.KuroSession
import com.wuwaconfig.app.config.KuroSessionStore
import com.wuwaconfig.app.config.OfficialCharacter
import com.wuwaconfig.app.config.PlayerInfo
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the player's own character view: Kuro passport login, in-game account choice,
 * character selection, and the per-character build (equipped gear plus the guide's
 * recommendations).
 *
 * The pure payload parsing lives in the `*Parser` objects and the transport in [KuroClient];
 * this view model is the state machine that sequences them. As with the app's other view
 * models it is not itself unit-tested — the parsers are the tested seam and the network flow
 * is exercised on device.
 */
class CharacterViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Application = getApplication<Application>()
    private val sessionStore = KuroSessionStore

    private var session: KuroSession? = null

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _loginBusy = MutableStateFlow(false)
    val loginBusy: StateFlow<Boolean> = _loginBusy.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    /** The stored email, to pre-fill the next login form. */
    private val _storedEmail = MutableStateFlow<String?>(null)
    val storedEmail: StateFlow<String?> = _storedEmail.asStateFlow()

    private val _players = MutableStateFlow<List<PlayerInfo>>(emptyList())
    val players: StateFlow<List<PlayerInfo>> = _players.asStateFlow()

    private val _chosenPlayer = MutableStateFlow<ChosenPlayer?>(null)
    val chosenPlayer: StateFlow<ChosenPlayer?> = _chosenPlayer.asStateFlow()

    private val _characters = MutableStateFlow<List<OfficialCharacter>>(emptyList())
    val characters: StateFlow<List<OfficialCharacter>> = _characters.asStateFlow()

    private val _rosterLoading = MutableStateFlow(false)
    val rosterLoading: StateFlow<Boolean> = _rosterLoading.asStateFlow()

    private val _selectedRoleGbId = MutableStateFlow<String?>(null)
    val selectedRoleGbId: StateFlow<String?> = _selectedRoleGbId.asStateFlow()

    private val _build = MutableStateFlow<CharacterBuild?>(null)
    val build: StateFlow<CharacterBuild?> = _build.asStateFlow()

    private val _buildLoading = MutableStateFlow(false)
    val buildLoading: StateFlow<Boolean> = _buildLoading.asStateFlow()

    private val _buildError = MutableStateFlow<String?>(null)
    val buildError: StateFlow<String?> = _buildError.asStateFlow()

    private var loginInFlight = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val stored = sessionStore.load(context)
            stored?.let { _storedEmail.value = it.email }
            if (stored != null && !isExpired(stored)) {
                session = stored
                _loggedIn.value = true
                _chosenPlayer.value = stored.chosenPlayer
                resume()
            }
        }
    }

    /** Runs the passport login; on success persists the session and loads the roster. */
    fun login(
        email: String,
        password: String,
    ) {
        if (loginInFlight) return
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            _loginError.value = "Enter your Kuro email and password."
            return
        }
        loginInFlight = true
        _loginBusy.value = true
        _loginError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            KuroClient.login(trimmedEmail, password)
                .onSuccess { newSession ->
                    session = newSession
                    sessionStore.save(context, newSession)
                    _loggedIn.value = true
                    _storedEmail.value = trimmedEmail
                    _chosenPlayer.value = null
                    _players.value = emptyList()
                    addLog("Kuro session established for ${newSession.username}")
                    resume()
                }
                .onFailure { e ->
                    _loginError.value = friendlyLoginError(e)
                    addLog("Kuro login failed: ${e.message}", LogLevel.WARNING)
                }
            _loginBusy.value = false
            loginInFlight = false
        }
    }

    /** Loads the public roster and the player's in-game accounts, then makes a default choice. */
    fun resume() {
        val s = session ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _rosterLoading.value = true
            KuroGuide.fetchCharacters()
                .onSuccess { _characters.value = it }
                .onFailure { addLog("Character roster unavailable", LogLevel.WARNING) }
            _rosterLoading.value = false
        }
        viewModelScope.launch(Dispatchers.IO) {
            KuroClient.fetchPlayers(s.xToken)
                .onSuccess { players ->
                    _players.value = players
                    val owned = players.filter { it.isSelectable }
                    val previous = s.chosenPlayer
                    val target =
                        previous?.let { chosen -> owned.find { it.playerId == chosen.playerId } }
                            ?: owned.maxByOrNull { it.level ?: 0 }
                    if (target != null) chooseAndStore(target)
                }
                .onFailure { e ->
                    _buildError.value = "Could not load your accounts: ${e.message}"
                    addLog("fetchPlayers failed: ${e.message}", LogLevel.WARNING)
                }
        }
    }

    /** Switches the active in-game account; subsequent builds resolve against it. */
    fun choosePlayer(player: PlayerInfo) {
        val s = session ?: return
        if (!player.isSelectable) return
        chooseAndStore(player)
    }

    private fun chooseAndStore(player: PlayerInfo) {
        val s = session ?: return
        val playerId = player.playerId ?: return
        val serverId = player.serverId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            KuroClient.choosePlayer(s.xToken, playerId, serverId)
                .onSuccess { chosen ->
                    val updated = s.copy(chosenPlayer = chosen)
                    session = updated
                    sessionStore.save(context, updated)
                    _chosenPlayer.value = chosen
                    // A re-selected account can have different equipment; refresh an open build.
                    _selectedRoleGbId.value?.let { selectCharacter(it, keepSelection = true) }
                }
                .onFailure { e ->
                    _buildError.value = "Could not select that account: ${e.message}"
                    addLog("choosePlayer failed: ${e.message}", LogLevel.WARNING)
                }
        }
    }

    /** Loads the selected character's build: the top curated build plus the player's equipped gear. */
    fun selectCharacter(
        roleGbId: String,
        keepSelection: Boolean = false,
    ) {
        val s = session ?: return
        if (!keepSelection) {
            _selectedRoleGbId.value = roleGbId
            _build.value = null
        }
        _buildError.value = null
        _buildLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            KuroClient.fetchBuilds(roleGbId, s.xToken)
                .onSuccess { builds ->
                    val top = builds.firstOrNull()
                    if (top == null) {
                        _buildLoading.value = false
                        _buildError.value = "No build data is available for this character."
                    } else {
                        KuroClient.fetchBuild(roleGbId, top.id, s.xToken)
                            .onSuccess { build ->
                                _buildLoading.value = false
                                _build.value = build
                            }
                            .onFailure { e ->
                                _buildLoading.value = false
                                _buildError.value = "Could not load the build: ${e.message}"
                                addLog("fetchBuild failed: ${e.message}", LogLevel.WARNING)
                            }
                    }
                }
                .onFailure { e ->
                    _buildLoading.value = false
                    _buildError.value = "Could not load the build: ${e.message}"
                    addLog("fetchBuilds failed: ${e.message}", LogLevel.WARNING)
                }
        }
    }

    /** Clears the stored session and every piece of derived state (logs the user out). */
    fun logout() {
        session = null
        sessionStore.clear(context)
        _loggedIn.value = false
        _players.value = emptyList()
        _chosenPlayer.value = null
        _characters.value = emptyList()
        _rosterLoading.value = false
        _selectedRoleGbId.value = null
        _build.value = null
        _buildLoading.value = false
        _buildError.value = null
    }

    private fun isExpired(session: KuroSession): Boolean {
        val expiry = session.expiresAtEpochSec ?: return false
        return System.currentTimeMillis() / 1000L >= expiry
    }

    private fun friendlyLoginError(e: Throwable): String =
        when (e) {
            is KuroClient.KuroLoginException ->
                when (e.step) {
                    "login" -> "Sign-in was rejected — check your email and password."
                    "token" -> "Sign-in did not complete. Try again."
                    "guide" -> "The guide could not start your session. Try again."
                    else -> "Network error reaching Kuro. Check your connection."
                }
            else -> "Sign-in failed: ${e.message ?: "unknown error"}"
        }

    private fun addLog(
        message: String,
        level: LogLevel = LogLevel.INFO,
    ) {
        LogRepository.add(message, level)
    }
}
