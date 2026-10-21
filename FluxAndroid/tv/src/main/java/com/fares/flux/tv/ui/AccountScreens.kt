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
import com.fares.flux.core.PairingReceiver
import com.fares.flux.core.QualityPreference
import com.fares.flux.core.SourceKind
import com.fares.flux.core.readableMessage
import com.fares.flux.core.receive
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
            Text("Connecte ton abonnement IPTV (Xtream Codes ou lien M3U), ou regarde les chaînes gratuites.", color = Secondary, fontSize = 14.sp)
            Button(onClick = { app.accounts.addFree(); onDone?.invoke() },
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().focusRequester(freeButton).focusProperties { right = fields }) {
                Text("Regarder les chaînes gratuites", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            // Quicker than the remote: the iPhone sends its account.
            Column(Modifier.padding(top = 15.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Depuis un autre appareil", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Sur l’appareil qui a le compte : Réglages › Comptes › Envoyer à un appareil, puis touche « Android TV » avec ce code.",
                    color = Secondary, fontSize = 13.sp)
                Text(receiver.code.toCharArray().joinToString("  "), color = FluxBlue, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.widthIn(max = 410.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { kind = if (kind == SourceKind.XTREAM) SourceKind.PLAYLIST else SourceKind.XTREAM },
                modifier = toFree.focusRequester(fields)) {
                Text(if (kind == SourceKind.XTREAM) "Type : Xtream Codes  ⇄  Lien M3U" else "Type : Lien M3U  ⇄  Xtream Codes",
                    Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            if (kind == SourceKind.PLAYLIST) {
                TextInput(server, { server = it }, "Lien de la liste (https://exemple.com/liste.m3u)", toFree)
            } else {
                TextInput(server, { server = it }, "Serveur (http://exemple.com:8080)", toFree)
                TextInput(username, { username = it }, "Identifiant", toFree)
                TextInput(password, { password = it }, "Mot de passe", toFree, password = true)
            }
            TextInput(name, { name = it }, "Nom du compte (facultatif)", toFree, next = false)
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
            ) { Text(if (checking) "Vérification…" else "Se connecter", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
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
    if (addsAccount) { SignInScreen { addsAccount = false }; return }
    val settings = app.settings

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 180.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SectionTitle("Comptes") }
        items(app.accounts.accounts, key = { it.id }) { account ->
            SettingRow(account.title, account.subtitle, if (account.id == app.accounts.activeID) "✓" else null,
                onLongClick = { removing = account }) { app.accounts.select(account.id) }
        }
        item { SettingRow("Ajouter un compte ou recevoir depuis l’iPhone", null, "+") { addsAccount = true } }
        app.accounts.info?.expiration?.takeIf { app.accounts.client?.isPanel == true }?.let { exp ->
            item { SettingRow("Expire le", null, DateFormat.getDateInstance(DateFormat.LONG, Locale.FRANCE).format(Date(exp * 1000))) {} }
        }
        item { SectionTitle("Direct") }
        item { SettingRow("Qualité", null, settings.quality.title) { picking = "quality" } }
        item { SettingRow("Numérotation", null, settings.numbering.title) { picking = "numbering" } }
        item {
            SettingRow("Chaînes en différé (+1 h, +2 h…)", null, if (settings.showsShifted) "Oui" else "Non") {
                settings.changeShowsShifted(!settings.showsShifted); app.catalog.optionsChanged()
            }
        }
        if (app.accounts.active?.credentials?.source != SourceKind.FREE) item {
            SettingRow("Flux gratuits en priorité (France 2, Arte…)", null, if (settings.prefersFree) "Oui" else "Non") {
                settings.changePrefersFree(!settings.prefersFree); app.catalog.optionsChanged()
            }
        }
        item { SectionTitle("Catalogue") }
        item {
            val updated = app.catalog.updated
            SettingRow("Actualiser le catalogue", null, when {
                app.catalog.isLoading -> "En cours…"
                updated != null -> ago(updated)
                else -> null
            }) { app.accounts.client?.let { c -> scope.launch { app.catalog.refresh(c) } } }
        }
    }

    when (picking) {
        "quality" -> ChoiceDialog("Qualité", QualityPreference.entries.map { it.raw to it.title }, settings.quality.raw, { picking = null }) {
            settings.changeQuality(QualityPreference.of(it)); picking = null
        }
        "numbering" -> ChoiceDialog("Numérotation", ChannelNumbering.entries.map { it.raw to it.title }, settings.numbering.raw, { picking = null }) {
            settings.changeNumbering(ChannelNumbering.of(it)); app.catalog.optionsChanged(); picking = null
        }
    }
    removing?.let { account ->
        Dialog(onDismissRequest = { removing = null }) {
            Column(Modifier.width(400.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(20.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Supprimer « ${account.title} » ?", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Ses identifiants, son catalogue et ses reprises quittent cet appareil.", color = Secondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { removing = null }) { Text("Annuler") }
                    Button(onClick = {
                        app.catalog.forget(account.credentials)
                        app.library.forget(account.id)
                        app.accounts.remove(account.id)
                        removing = null
                    }) { Text("Supprimer") }
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

/** "il y a 3 min", in French whatever the box's language. */
private fun ago(millis: Long): String {
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        minutes < 1 -> "à l’instant"
        minutes < 60 -> "il y a $minutes min"
        minutes < 24 * 60 -> "il y a ${minutes / 60} h"
        else -> "il y a ${minutes / (24 * 60)} j"
    }
}
