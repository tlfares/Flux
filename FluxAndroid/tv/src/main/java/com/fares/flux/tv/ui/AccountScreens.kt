package com.fares.flux.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.fares.flux.core.Account
import com.fares.flux.core.ChannelNumbering
import com.fares.flux.core.Credentials
import com.fares.flux.core.FreeChannels
import com.fares.flux.core.PairingReceiver
import com.fares.flux.core.QualityPreference
import com.fares.flux.core.SourceKind
import com.fares.flux.core.readableMessage
import com.fares.flux.core.receive
import com.fares.flux.core.tr
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** The first screen, and adding an account later: typed in, or sent from the iPhone. */
@Composable
fun SignInScreen(onDone: (() -> Unit)? = null) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(SourceKind.XTREAM) }
    var checking by remember { mutableStateOf(false) }
    // Left from the fields goes to the free channels: it sits lower, and the
    // nearest thing to the left by position was the tab row.
    val freeButton = remember { androidx.compose.ui.focus.FocusRequester() }
    val fields = remember { androidx.compose.ui.focus.FocusRequester() }
    val toFree = Modifier.fillMaxWidth().focusProperties { left = freeButton }
    var error by remember { mutableStateOf<String?>(null) }
    if (onDone != null) BackHandler { onDone() }

    val receiver = remember {
        PairingReceiver(context) { message ->
            if (!checking) scope.launch {
                checking = true; error = null
                runCatching { app.receive(message) }.onSuccess { onDone?.invoke() }.onFailure { error = it.readableMessage }
                checking = false
            }
        }
    }
    DisposableEffect(Unit) {
        receiver.start()
        onDispose { receiver.stop() }
    }

    Row(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(FluxBlue.copy(alpha = 0.35f), Color.Black), center = androidx.compose.ui.geometry.Offset.Zero, radius = 1400f))
            .padding(40.dp),
        horizontalArrangement = Arrangement.spacedBy(50.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Flux", fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Text(tr("Connecte ton abonnement IPTV (Xtream Codes ou lien M3U), ou regarde les chaînes gratuites.", "Connect your IPTV subscription (Xtream Codes or M3U link), or watch free channels."), color = Secondary, fontSize = 14.sp)
            Button(onClick = { app.accounts.addFree(); onDone?.invoke() },
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().focusRequester(freeButton).focusProperties { right = fields }) {
                Text(tr("Regarder les chaînes gratuites", "Watch free channels"), Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            // Quicker than the remote: the iPhone sends its account.
            Column(Modifier.padding(top = 15.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(tr("Depuis un autre appareil", "From another device"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(tr("Sur l’appareil qui a le compte : Réglages › Comptes › Envoyer à un appareil, puis touche « Android TV » avec ce code.", "On the device that has the account, go to Settings › Accounts › Send to a device, then tap “Android TV” with this code."),
                    color = Secondary, fontSize = 13.sp)
                Text(receiver.code.toCharArray().joinToString("  "), color = FluxBlue, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.widthIn(max = 410.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { kind = if (kind == SourceKind.XTREAM) SourceKind.PLAYLIST else SourceKind.XTREAM },
                modifier = toFree.focusRequester(fields)) {
                Text(if (kind == SourceKind.XTREAM) tr("Type : Xtream Codes  ⇄  Lien M3U", "Type: Xtream Codes  ⇄  M3U link") else tr("Type : Lien M3U  ⇄  Xtream Codes", "Type: M3U link  ⇄  Xtream Codes"),
                    Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            if (kind == SourceKind.PLAYLIST) {
                TextInput(server, { server = it }, tr("Lien de la liste (https://exemple.com/liste.m3u)", "Playlist link (https://example.com/playlist.m3u)"), toFree)
            } else {
                TextInput(server, { server = it }, tr("Serveur (http://exemple.com:8080)", "Server (http://example.com:8080)"), toFree)
                TextInput(username, { username = it }, tr("Identifiant", "Username"), toFree)
                TextInput(password, { password = it }, tr("Mot de passe", "Password"), toFree, password = true)
            }
            TextInput(name, { name = it }, tr("Nom du compte (facultatif)", "Account name (optional)"), toFree, next = false)
            error?.let { Text(it, color = Color(0xFFFF453A), fontSize = 13.sp) }
            Button(
                onClick = {
                    scope.launch {
                        checking = true; error = null
                        val credentials = if (kind == SourceKind.PLAYLIST) Credentials.playlist(server) else Credentials(server.trim(), username.trim(), password)
                        runCatching { app.accounts.add(Account(name = name.trim(), credentials = credentials)) }
                            .onSuccess { onDone?.invoke() }.onFailure { error = it.readableMessage }
                        checking = false
                    }
                },
                enabled = !checking && server.isNotBlank() && (kind == SourceKind.PLAYLIST || (username.isNotBlank() && password.isNotEmpty())),
                modifier = toFree,
            ) { Text(if (checking) tr("Vérification…", "Checking…") else tr("Se connecter", "Sign in"), Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var addsAccount by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Account?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }
    var clearing by remember { mutableStateOf(false) }
    if (addsAccount) { SignInScreen { addsAccount = false }; return }
    val settings = app.settings

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 180.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SectionTitle(tr("Comptes", "Accounts")) }
        items(app.accounts.accounts, key = { it.id }) { account ->
            SettingRow(account.title, account.subtitle, if (account.id == app.accounts.activeID) "✓" else null,
                onLongClick = { removing = account }) { app.accounts.select(account.id) }
        }
        item { SettingRow(tr("Ajouter un compte ou recevoir depuis l’iPhone", "Add an account or receive one from an iPhone"), null, "+") { addsAccount = true } }
        app.accounts.info?.expiration?.takeIf { app.accounts.client?.isPanel == true }?.let { exp ->
            item { SettingRow(tr("Expire le", "Expires"), null, DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault()).format(Date(exp * 1000))) {} }
        }
        // The free channels have no qualities, delays nor subscription: their country comes first instead.
        val free = app.accounts.active?.credentials?.source == SourceKind.FREE
        item { SectionTitle(tr("Direct", "Live")) }
        if (free) item {
            SettingRow(tr("Chaînes gratuites", "Free channels"), FreeChannels.countriesNote(settings.freeCountry),
                FreeChannels.choiceFlag(settings.freeCountry) + " " + FreeChannels.choiceTitle(settings.freeCountry)) { picking = "countries" }
        }
        else item { SettingRow(tr("Qualité", "Quality"), null, settings.quality.title) { picking = "quality" } }
        item { SettingRow(tr("Numérotation", "Numbering"), null, settings.numbering.title) { picking = "numbering" } }
        if (!free) item {
            SettingRow(tr("Chaînes en différé (+1 h, +2 h…)", "Time-shifted channels (+1 h, +2 h…)"), null, if (settings.showsShifted) tr("Oui", "On") else tr("Non", "Off")) {
                settings.changeShowsShifted(!settings.showsShifted); app.catalog.optionsChanged()
            }
        }
        if (!free) item {
            SettingRow(tr("Flux gratuits en priorité (France 2, Arte…)", "Prefer free streams (France 2, Arte…)"), null, if (settings.prefersFree) tr("Oui", "On") else tr("Non", "Off")) {
                settings.changePrefersFree(!settings.prefersFree); app.catalog.optionsChanged()
            }
        }
        item { SettingRow("DNS", tr("Si ta box bloque ton fournisseur, essaie un DNS public.", "If your router blocks your provider, try a public DNS."), settings.dns.label) { picking = "dns" } }
        // Only where the guide has something to show.
        if (app.hasGuide) item {
            SettingRow(tr("Onglet Guide", "Guide tab"), tr("Les programmes de toutes les chaînes sur une grille.", "What’s on every channel, on a grid."),
                if (settings.guideTab) tr("Oui", "On") else tr("Non", "Off")) { settings.changeGuideTab(!settings.guideTab) }
        }
        // Only for an account that has some: the free channels have none.
        if (app.hasMovies) item {
            SettingRow(tr("Onglet Films", "Movies tab"), null, if (settings.moviesTab) tr("Oui", "On") else tr("Non", "Off")) { settings.changeMoviesTab(!settings.moviesTab) }
        }
        if (app.hasSeries) item {
            SettingRow(tr("Onglet Séries", "Series tab"), null, if (settings.seriesTab) tr("Oui", "On") else tr("Non", "Off")) { settings.changeSeriesTab(!settings.seriesTab) }
        }
        item { SectionTitle(tr("Catalogue", "Catalog")) }
        item {
            val updated = app.catalog.updated
            SettingRow(tr("Actualiser le catalogue", "Refresh catalog"), null, when {
                app.catalog.isLoading -> tr("En cours…", "Refreshing…")
                updated != null -> ago(updated)
                else -> null
            }) { app.accounts.client?.let { c -> scope.launch { app.catalog.refresh(c) } } }
        }
        item {
            SettingRow(tr("Effacer l’historique", "Clear history"), tr("Les chaînes récentes et la reprise des films et épisodes.", "Recent channels and where films and episodes stopped."), null) { clearing = true }
        }
    }

    if (clearing) Dialog(onDismissRequest = { clearing = false }) {
        Column(Modifier.width(400.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(20.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr("Effacer l’historique ?", "Clear history?"), fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(tr("Les chaînes récentes et la reprise des films et épisodes seront oubliées.", "Recent channels and where films and episodes stopped will be forgotten."), color = Secondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { clearing = false }) { Text(tr("Annuler", "Cancel")) }
                Button(onClick = { app.library.clearHistory(); clearing = false }) { Text(tr("Effacer", "Clear")) }
            }
        }
    }

    when (picking) {
        "quality" -> ChoiceDialog(tr("Qualité", "Quality"), QualityPreference.entries.map { it.raw to it.title }, settings.quality.raw, { picking = null }) {
            settings.changeQuality(QualityPreference.of(it)); picking = null
        }
        "countries" -> ChoiceDialog(tr("Chaînes gratuites", "Free channels"), FreeChannels.choices.map { it to FreeChannels.choiceFlag(it) + " " + FreeChannels.choiceTitle(it) },
            settings.freeCountry, { picking = null }) {
            settings.changeFreeCountry(it); app.catalog.optionsChanged(); picking = null
        }
        "dns" -> ChoiceDialog("DNS", com.fares.flux.core.DnsProvider.entries.map { it.raw to it.label }, settings.dns.raw, { picking = null }) {
            settings.changeDns(com.fares.flux.core.DnsProvider.of(it)); picking = null
            app.accounts.client?.let { c -> scope.launch { app.catalog.refresh(c) } }
        }
        "numbering" -> ChoiceDialog(tr("Numérotation", "Numbering"), ChannelNumbering.entries.map { it.raw to it.title }, settings.numbering.raw, { picking = null }) {
            settings.changeNumbering(ChannelNumbering.of(it)); app.catalog.optionsChanged(); picking = null
        }
    }
    removing?.let { account ->
        Dialog(onDismissRequest = { removing = null }) {
            Column(Modifier.width(400.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(20.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(tr("Supprimer « ${account.title} » ?", "Remove “${account.title}”?"), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(tr("Ses identifiants, son catalogue et ses reprises quittent cet appareil.", "Its login, catalog and watch progress will be removed from this device."), color = Secondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { removing = null }) { Text(tr("Annuler", "Cancel")) }
                    Button(onClick = {
                        app.catalog.forget(account.credentials)
                        app.library.forget(account.id)
                        app.categoryLayouts.forget(account.id)
                        app.accounts.remove(account.id)
                        removing = null
                    }) { Text(tr("Supprimer", "Remove")) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title.uppercase(), Modifier.padding(top = 14.dp, bottom = 2.dp, start = 6.dp), color = Tertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

/** A settings row: white, dark once focus turns its cell white. */
@Composable
private fun SettingRow(title: String, subtitle: String?, value: String?, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    FocusRow(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp)
                subtitle?.let { Text(it, fontSize = 12.sp, color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.6f)) }
            }
            Spacer(Modifier.width(12.dp))
            value?.let { Text(it, fontSize = 15.sp, color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.6f)) }
        }
    }
}

/** The whole window while there's no account yet. */
@Composable
fun Centered(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }

/** "il y a 3 min", "3 min ago": in the box's language. */
private fun ago(millis: Long): String {
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    val days = minutes / (24 * 60)
    return when {
        minutes < 1 -> tr("à l’instant", "just now")
        minutes < 60 -> tr("il y a $minutes min", "$minutes min ago")
        minutes < 24 * 60 -> tr("il y a ${minutes / 60} h", "${minutes / 60} hr ago")
        else -> tr("il y a $days j", if (days == 1L) "1 day ago" else "$days days ago")
    }
}
