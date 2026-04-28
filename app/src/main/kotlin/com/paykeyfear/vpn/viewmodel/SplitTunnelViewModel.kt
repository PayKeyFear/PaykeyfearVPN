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
    "com.vk.vkvideo",
    "com.uma.musicvk",
    "live.vkplay.app",
    "ru.mail.mailapp",
    "ru.mail.auth.app",
    "ru.mail.calendar",
    "ru.mail.cloud",
    "com.mailru.icq",
    "ru.ok.android",
    "com.odnoklassniki.android",
    "ru.ok.games",
    "ru.zen.android",
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
    "com.yandex.aliceapp",
    "com.yandex.plus",
    "com.yandex.searchapp",
    "com.yandex.lavka",
    "com.yandex.shedevrus",
    "ru.yandex.searchplugin",
    "ru.yandex.metro",
    "ru.yandex.weatherplugin",
    "ru.yandex.yandexnavi",
    "ru.yandex.yandexmaps",
    "ru.yandex.mail",
    "ru.yandex.music",
    "ru.yandex.direct",
    "ru.yandex.afisha",
    "ru.yandex.realty",
    "ru.yandex.telemost",
    "ru.yandex.androidkeyboard",
    // Sber
    "ru.sberbank.android.ru.mbfa",
    "ru.sberbank_retail.android",
    "com.sberbank.sbbol",
    "ru.sberbank.spasibo",
    "ru.sbrf.android.ru.mbfa",
    "ru.sberbank.sbermegamarket",
    "ru.sberbank.sberzvuki",
    "ru.sber.smartmarket",
    "ru.sber.sberprime",
    "ru.sber.telecom",
    "com.sberbank.sberpravo",
    "ru.sberbankmobile",
    // Tinkoff / T-Bank
    "com.idamob.tinkoff.android",
    "ru.idamob.tinkoff.android",
    "ru.tinkoff.banking.android",
    "ru.tinkoff.invest",
    "ru.tinkoff.investing",
    "ru.tinkoff.sme",
    "ru.tinkoff.mvno",
    "com.tinkoff.news",
    "com.yandex.bank",
    // Banks
    "ru.alfabank.mobile.android",
    "ru.alfabank.orussia",
    "ru.alfadirect.app",
    "ru.vtb24.mobilebanking.android",
    "ru.vtbmobile.app",
    "ru.raiffeisen.android",
    "ru.gazprombank.android",
    "ru.gazprombank.android.mobilebank.app",
    "ru.rosbank.android",
    "ru.mtsbank.mtsdengi",
    "ru.sovcombank.halvacard",
    "ru.psbank.mobile",
    "ru.otkritie.mobile",
    "ru.uralsib.mobile",
    "ru.bspb.mobilebank",
    "ru.rocketbank.r",
    "ru.rshb.mobilebank",
    "ru.otpbank.mobile",
    "ru.nspk.mirpay",
    "ru.nspk.sbpay",
    "ru.bankuralsib.mb.android",
    "ru.letobank.Prometheus",
    "ru.gpbmobile.lk",
    "com.idamobile.android.LockoBank",
    "ru.moex.app",
    "ru.ozon.fintech.finance",
    // E-commerce / marketplaces
    "ru.ozon.app.android",
    "travel.ozon.mobile",
    "ru.wildberries.b2b",
    "com.wildberries.ru",
    "wb.partners",
    "wildberries.business",
    "ru.avito",
    "com.avito.android",
    "ru.youla",
    "com.allgoritm.youla",
    "com.kazanexpress.ke_app",
    "ru.lamoda.android",
    "ru.lamoda.lite",
    "ru.leroymerlin.domclick",
    "ru.leroymerlin.mobile",
    "ru.domclick.mobile",
    "ru.citilink",
    "ru.mvideo.app",
    "com.mvideo.app",
    "ru.sportmaster.app",
    "ru.letu.app",
    "ru.fixprice.app",
    "ru.megamarket.marketplace",
    "ru.beru.android",
    "com.logistic.sdek",
    "com.deliveryclub",
    "ru.instamart",
    // Classifieds / transport / travel
    "ru.auto.ara",
    "com.drive2",
    "ru.cian.main",
    "ru.drom.app",
    "ru.farpost.app",
    "ru.aviasales",
    "ru.rzd.loyalty",
    "ru.aeroflot.shopping",
    "ru.pobeda.pobeda",
    "ru.gibdd_pay.app",
    "ru.dublgis.dgismobile",
    // Jobs
    "com.hh.android",
    "ru.superjob.android",
    "ru.zarplata.android",
    // Government / postal
    "com.gosuslugi.client",
    "ru.gosuslugi",
    "ru.nalog.rmr.client",
    "ru.rosreestr.mobile",
    "ru.fanid",
    "ru.mos.mos",
    "ru.mos.polls",
    "ru.mosgorpass",
    "ru.mosmetro.metro",
    "ru.mosparking.appnew",
    "com.russianpost.tracking",
    "ru.russianpost.pechkin",
    "com.octopod.russianpost.client.android",
    "ru.trudvsem.mobile",
    "ru.cbr.banknotesrf",
    // Media / news
    "ru.kinopoisk.android",
    "ru.kinopoisk.hd",
    "com.rutube.ru",
    "ru.ivi.client",
    "ru.more.tv",
    "ru.start.androidmobile",
    "gpm.tnt_premier",
    "one.premier.rustoretv",
    "ru.okko.tv",
    "tv.okko.androidtv",
    "ru.wink.app",
    "ru.livetv",
    "ru.smotrim.ru",
    "com.vgtrk.smotrim",
    "ru.rambler.news",
    "ru.rbc.news",
    "ru.rt.video.app",
    "ru.afisha.android",
    "ru.litres.android",
    "ru.mybook",
    "ru.plus.bookmate",
    // Music / radio
    "ru.zaycev.net",
    "free.zaycev.net",
    "ru.zvuk",
    "com.zvooq.openplay",
    "ru.mts.music.android",
    // Telecom operators
    "ru.mts.mymts",
    "ru.mts.android.mtslogin",
    "com.mts.who_calls",
    "ru.beeline.services",
    "ru.beeline.b2b",
    "ru.megafon.mlk",
    "ru.megafon.megafon360",
    "ru.systtech.megafon.mobile",
    "megafon.transport.mobile",
    "ru.tele2.cabinet",
    "ru.tele2.mytele2",
    "tele2.vats",
    "ru.yota.android",
    "ru.tinkoff.mvno",
    // Utilities / security
    "com.2ip.checker",
    "ru.kaspersky.kes",
    "com.kaspersky.kms",
    "com.drweb",
    "ru.drweb.security",
    "com.drweb.lite",
    // Food delivery / restaurants
    "ru.kfc.kfc_delivery",
    "com.apegroup.mcdonaldsrussia",
    "ru.burgerking",
    "ru.vkusvill",
    // Streaming / media (additional)
    "ru.rutube.app",
    "ru.more.play",
    // E-commerce (additional)
    "ru.dns.shop.android",
    // Government / identity (additional)
    "ru.gosuslugi.goskey",
    "com.gnivts.selfemployed",
    // Telecom / ISP
    "ru.rostel",
    "ru.ufanet.smartphone",
    // VK services
    "ru.vk.store",
    // Yandex (additional)
    "ru.yandex.cloud",
    "ru.yandex.taxi",
    "ru.yandex.key",
    "ru.yandex.rasp",
    "ru.yandex.disk",
    "com.yandex.yamb",
    "com.yandex.iot",
    // Kinopoisk
    "ru.kinopoisk",
    // Jobs (additional)
    "ru.hh.android",
    // Banking (additional)
    "ru.lewis.dbo",
    // Home / smart devices
    "home.tuvio.com",
    // Food delivery
    "com.edadeal.android",
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
