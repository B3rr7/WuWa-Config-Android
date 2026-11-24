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

    private val _rosterError = MutableStateFlow<String?>(null)

    /** Non-null when the public roster fetch failed; the view offers a retry for it. */
    val rosterError: StateFlow<String?> = _rosterError.asStateFlow()

    private val _selectedRoleGbId = MutableStateFlow<String?>(null)
    val selectedRoleGbId: StateFlow<String?> = _selectedRoleGbId.asStateFlow()

    private val _build = MutableStateFlow<CharacterBuild?>(null)
    val build: StateFlow<CharacterBuild?> = _build.asStateFlow()

    private val _buildLoading = MutableStateFlow(false)
    val buildLoading: StateFlow<Boolean> = _buildLoading.asStateFlow()

    private val _buildError = MutableStateFlow<String?>(null)
    val buildError: StateFlow<String?> = _buildError.asStateFlow()

    /**
     * True when the guide refused the stored credential, as opposed to a transient failure.
     *
     * The guide answers a dead `x-token` with HTTP 200 and `{"code":500}` — a body the
     * parsers read as "no data" — so before this the screen kept pretending to be signed in
     * while every request was rejected, with no error and no way forward. When this flips,
     * the session is dropped and the login form is shown with the reason.
     */
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

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
                .onSuccess {
                    _characters.value = it
                    _rosterError.value = null
                }
                .onFailure { e ->
                    // The view separates "fetch failed" from "roster is empty" so an outage
                    // offers a retry instead of reading as an account with no characters.
                    _rosterError.value = "Could not load the character roster: ${KuroClient.describeGuideError(e)}"
                    addLog("Character roster unavailable: ${e.message}", LogLevel.WARNING)
                }
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
                    // A rejected credential is not a transient failure: drop the session so the
                    // login form replaces the dead panel, rather than leaving the user tapping
                    // characters that can never resolve.
                    if (KuroClient.isSessionRejection(e)) {
                        expireSession("Your Kuro session expired. Sign in again to read your account.")
                    } else {
                        _buildError.value = "Could not load your accounts: ${KuroClient.describeGuideError(e)}"
                    }
                    addLog("fetchPlayers failed: ${e.message}", LogLevel.WARNING)
                }
        }
    }

    /**
     * Re-runs the outstanding fetches after a failure.
     *
     * Both error paths used to be one-way: a failed roster left the screen saying "No
     * characters loaded yet" and a failed build left a message with no way forward short of
     * signing out. This is what the view's error cards call.
     */
    fun refresh() {
        _rosterError.value = null
        _buildError.value = null
        _sessionExpired.value = false
        resume()
        _selectedRoleGbId.value?.let { selectCharacter(it, keepSelection = true) }
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
                    if (builds.isEmpty()) {
                        _buildLoading.value = false
                        _buildError.value = "No build data is available for this character."
                        return@onSuccess
                    }
                    // Not every id in `/introduction/list` resolves. The list is popularity-
                    // ordered now, but the guide still answers `data: null` for the archived and
                    // community-only ids — so probe down the list instead of pinning the whole
                    // screen to one id. The first non-null answer wins.
                    var lastFailure: Throwable? = null
                    for (candidate in builds.take(MAX_BUILD_PROBES)) {
                        val attempt = KuroClient.fetchBuild(roleGbId, candidate.id, s.xToken)
                        val failure = attempt.exceptionOrNull()
                        if (failure != null) {
                            // A rejected credential ends the walk; anything else is worth one
                            // more id, because the guide 500s individual stale builds too.
                            if (KuroClient.isSessionRejection(failure)) {
                                failBuild(failure)
                                return@onSuccess
                            }
                            lastFailure = failure
                            continue
                        }
                        val build = attempt.getOrNull()
                        if (build != null) {
                            _buildLoading.value = false
                            _build.value = build
                            return@onSuccess
                        }
                    }
                    _buildLoading.value = false
                    _buildError.value =
                        lastFailure
                            ?.let { "Could not load the build: ${KuroClient.describeGuideError(it)}" }
                            ?: "The guide has no build details for this character yet."
                    lastFailure?.let { addLog("fetchBuild failed: ${it.message}", LogLevel.WARNING) }
                }
                .onFailure { e -> failBuild(e) }
        }
    }

    private fun failBuild(e: Throwable) {
        _buildLoading.value = false
        if (KuroClient.isSessionRejection(e)) {
            expireSession("Your Kuro session expired. Sign in again to read your builds.")
        } else {
            _buildError.value = "Could not load the build: ${KuroClient.describeGuideError(e)}"
        }
        addLog("fetchBuild failed: ${e.message}", LogLevel.WARNING)
    }

    /**
     * Drops a credential the guide no longer accepts and returns the user to the login form.
     *
     * Called only on an explicit rejection, never on a timeout — a flaky network must not cost
     * the user a sign-in.
     */
    private fun expireSession(reason: String) {
        session = null
        sessionStore.clear(context)
        _loggedIn.value = false
        _players.value = emptyList()
        _chosenPlayer.value = null
        _selectedRoleGbId.value = null
        _build.value = null
        _buildLoading.value = false
        _buildError.value = null
        _sessionExpired.value = true
        _loginError.value = reason
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
        _rosterError.value = null
        _selectedRoleGbId.value = null
        _build.value = null
        _buildLoading.value = false
        _buildError.value = null
        _loginError.value = null
        _sessionExpired.value = false
    }

    private fun isExpired(session: KuroSession): Boolean {
        val expiry = session.expiresAtEpochSec ?: return false
        return System.currentTimeMillis() / 1000L >= expiry
    }

    private companion object {
        /**
         * How many `/introduction/info` ids to probe before giving up.
         *
         * The working build sat within the first two entries of every character checked live on
         * 2026-11-19; five leaves room for a run of stale community ids without a request storm.
         */
        const val MAX_BUILD_PROBES = 5
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
