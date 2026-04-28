package com.paykeyfear.vpn.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paykeyfear.vpn.core.model.SplitTunnelMode
import com.paykeyfear.vpn.core.model.TunnelState
import com.paykeyfear.vpn.data.prefs.PreferencesRepository
import com.paykeyfear.vpn.service.TunnelController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isRussian: Boolean = false,
)

val RU_PACKAGES: Set<String> = setOf(
    // VK / Mail.ru group
    "com.vkontakte.android",
    "com.vk.vkcompose",
    "com.vk.calls",
    "com.vk.im",
    "ru.mail.mailapp",
    "ru.mail.auth.app",
    "ru.mail.calendar",
    "ru.mail.cloud",
    "com.mailru.icq",
    "ru.ok.android",
    "com.odnoklassniki.android",
    "ru.ok.games",
    // Yandex
    "com.yandex.browser",
    "com.yandex.browser.beta",
    "com.yandex.browser.lite",
    "com.yandex.launcher",
    "com.yandex.mail",
    "com.yandex.maps",
    "com.yandex.market",
    "com.yandex.taxi",
    "com.yandex.taxi.driver",
    "ru.yandex.taximeter",
    "ru.yandex.taximeter.beta",
    "com.yandex.music",
    "com.yandex.disk",
    "com.yandex.translate",
    "com.yandex.zen",
    "com.yandex.video",
    "com.yandex.alice",
    "com.yandex.plus",
    "com.yandex.searchapp",
    "ru.yandex.searchplugin",
    "ru.yandex.metro",
    "ru.yandex.weatherplugin",
    "ru.yandex.yandexnavi",
    "ru.yandex.direct",
    "ru.yandex.afisha",
    "ru.yandex.realty",
    // Sber
    "ru.sberbank.android.ru.mbfa",
    "ru.sberbank_retail.android",
    "com.sberbank.sbbol",
    "ru.sberbank.spasibo",
    "ru.sbrf.android.ru.mbfa",
    "ru.sberbank.sbermegamarket",
    "ru.sberbank.sberzvuki",
    "ru.sberbank.sbolineeducation",
    "ru.sber.smartmarket",
    "ru.sber.sberprime",
    // Tinkoff / T-Bank
    "com.idamob.tinkoff.android",
    "ru.tinkoff.banking.android",
    "ru.tinkoff.acquiring.sample",
    "com.tinkoff.news",
    "ru.tinkoff.invest",
    // Banks
    "ru.alfabank.mobile.android",
    "ru.alfabank.orussia",
    "ru.vtb24.mobilebanking.android",
    "ru.raiffeisen.android",
    "ru.gazprombank.android",
    "ru.rosbank.android",
    "ru.mtsbank.mtsdengi",
    "ru.sovcombank.halvacard",
    "ru.psbank.mobile",
    "ru.otkritie.mobile",
    "ru.uralsib.mobile",
    "ru.bspb.mobilebank",
    "ru.rocketbank.r",
    // E-commerce / marketplaces
    "ru.ozon.app.android",
    "ru.wildberries.b2b",
    "com.wildberries.ru",
    "ru.avito",
    "com.avito.android",
    "ru.youla",
    "com.sbermegamarket.app",
    "ru.lamoda.android",
    "ru.leroymerlin.domclick",
    "ru.domclick.mobile",
    // Classifieds / transport / travel
    "ru.auto.ara",
    "ru.cian.main",
    "ru.rzd.loyalty",
    "ru.aeroflot.shopping",
    "ru.pobeda.pobeda",
    "ru.uremont.app",
    "ru.citymobil.driver",
    // Jobs
    "com.hh.android",
    "ru.superjob.android",
    "ru.zarplata.android",
    // Government / postal
    "com.gosuslugi.client",
    "ru.gosuslugi",
    "ru.mos.mos",
    "ru.mos.polls",
    "com.russianpost.tracking",
    "ru.pochta.tracking",
    // Media / entertainment
    "ru.kinopoisk.android",
    "ru.kinopoisk.hd",
    "com.rutube.ru",
    "ru.ivi.client",
    "ru.more.tv",
    "ru.start.tv",
    "ru.okko.tv",
    "ru.premier.client",
    "ru.livetv",
    "ru.rambler.news",
    "ru.rbc.news",
    "ru.fontanka.news",
    "com.novayagazeta",
    "ru.kommersant.reader",
    "com.sports.ru",
    // Music / radio
    "ru.zaycev.net",
    "ru.smotrim.ru",
    "ru.zvuk",
    // Telecom operators
    "ru.mts.android.mtslogin",
    "ru.mts.mymts",
    "com.mts.mtsapp",
    "ru.beeline.b2b",
    "ru.beeline.services",
    "ru.megafon.megafon360",
    "com.tele2.russia",
    "ru.tele2.cabinet",
    // Utilities / tools
    "com.2ip.checker",
    "ru.kaspersky.kes",
    "com.drweb",
    "ru.drweb.security",
    "com.drweb.lite",
)

data class SplitTunnelUiState(
    val mode: SplitTunnelMode = SplitTunnelMode.Off,
    val selected: Set<String> = emptySet(),
    val apps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = true,
    val query: String = "",
    val ruBypass: Boolean = false,
    val vpnActive: Boolean = false,
) {
    private val nonSystem: List<InstalledApp>
        get() {
            val base = apps.filter { !it.isSystem }
            return if (query.isBlank()) {
                base
            } else {
                val q = query.trim().lowercase()
                base.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
            }
        }

    val filtered: List<InstalledApp> get() = nonSystem

    val filteredRu: List<InstalledApp> get() = nonSystem.filter { it.isRussian }

    val filteredOther: List<InstalledApp> get() = nonSystem.filter { !it.isRussian }
}

@HiltViewModel
class SplitTunnelViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesRepository,
    private val tunnelController: TunnelController,
) : ViewModel() {
    private val apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val query = MutableStateFlow("")

    val state: StateFlow<SplitTunnelUiState> =
        combine(
            preferences.splitTunnelMode,
            preferences.splitTunnelPackages,
            preferences.ruBypassEnabled,
            apps,
            combine(loading, query, tunnelController.state) { l, q, ts -> Triple(l, q, ts) },
        ) { mode, pkgs, ruBypass, list, (isLoading, q, tunnelState) ->
            // selected first (false < true), then user apps, then alpha
            val sorted = list.sortedWith(
                compareBy(
                    { it.packageName !in pkgs },
                    { it.isSystem },
                    { it.label.lowercase() },
                ),
            )
            SplitTunnelUiState(
                mode = mode,
                selected = pkgs,
                apps = sorted,
                isLoading = isLoading,
                query = q,
                ruBypass = ruBypass,
                vpnActive = tunnelState is TunnelState.Connected || tunnelState == TunnelState.Connecting,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SplitTunnelUiState(),
        )

    init {
        viewModelScope.launch {
            apps.value = loadInstalledApps()
            loading.value = false
        }
    }

    fun setMode(mode: SplitTunnelMode) {
        if (state.value.vpnActive) return
        viewModelScope.launch { preferences.setSplitTunnelMode(mode) }
    }

    fun resetRules() {
        if (state.value.vpnActive) return
        viewModelScope.launch {
            preferences.setSplitTunnelPackages(emptySet())
            preferences.setSplitTunnelMode(SplitTunnelMode.Off)
        }
    }

    fun toggle(pkg: String, checked: Boolean) {
        if (state.value.vpnActive) return
        viewModelScope.launch {
            // Don't read state.value — it's produced by a WhileSubscribed
            // StateFlow that stays at initialValue until someone actually
            // collects it, so in unit tests the set we write back is
            // computed against an empty seed. Reading the underlying
            // preferences flow directly gives us the ground truth
            // regardless of collection timing.
            val current = preferences.splitTunnelPackages.first()
            val next = current.toMutableSet().apply {
                if (checked) add(pkg) else remove(pkg)
            }
            preferences.setSplitTunnelPackages(next)
        }
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun setRuBypass(enabled: Boolean) {
        if (state.value.vpnActive) return
        viewModelScope.launch { preferences.setRuBypassEnabled(enabled) }
    }

    fun selectAllRussianAppsBypass() {
        if (state.value.vpnActive) return
        viewModelScope.launch {
            val current = preferences.splitTunnelPackages.first()
            val ruInstalled = apps.value
                .filter { it.isRussian && !it.isSystem }
                .map { it.packageName }
                .toSet()
            preferences.setSplitTunnelPackages(current + ruInstalled)
            preferences.setSplitTunnelMode(SplitTunnelMode.Denylist)
        }
    }

    private suspend fun loadInstalledApps(): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledPackages(PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS)
                .asSequence()
                .filter { it.packageName != context.packageName }
                .filter { pkg ->
                    // Only show apps that have network needs.
                    pkg.requestedPermissions?.any { it == android.Manifest.permission.INTERNET } == true
                }
                .map { pkg ->
                    val ai = pkg.applicationInfo
                    InstalledApp(
                        packageName = pkg.packageName,
                        label = ai?.loadLabel(pm)?.toString() ?: pkg.packageName,
                        isSystem = ai != null &&
                            (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                        isRussian = pkg.packageName in RU_PACKAGES,
                    )
                }
                .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
                .toList()
        }
}
