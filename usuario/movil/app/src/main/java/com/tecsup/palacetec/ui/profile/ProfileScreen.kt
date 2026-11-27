package com.tecsup.palacetec.ui.profile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.palacetec.R
import com.tecsup.palacetec.data.remote.Profile
import com.tecsup.palacetec.ui.appViewModel
import com.tecsup.palacetec.ui.components.Avatar
import com.tecsup.palacetec.ui.components.ErrorBox
import com.tecsup.palacetec.ui.components.FormField
import com.tecsup.palacetec.ui.components.LoadingBox
import com.tecsup.palacetec.ui.components.PresetAvatars
import com.tecsup.palacetec.ui.components.PrimaryButton
import com.tecsup.palacetec.ui.theme.VerdeGanancia

/** Accesos que se muestran debajo de los datos. */
class ProfileLink(val label: String, @param:DrawableRes val icon: Int, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    links: List<ProfileLink>,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = appViewModel { ProfileViewModel(it.profileRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) {
        viewModel.load()
        onStopOrDispose { }
    }
    BackHandler(enabled = state.form != null, onBack = viewModel::cancelEdit)

    if (state.loading && state.profile == null) {
        LoadingBox(modifier)
        return
    }
    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = viewModel::load,
        modifier = modifier.fillMaxSize().imePadding(),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.loadError?.let { ErrorBox(it, viewModel::load) }
            state.profile?.let { profile ->
                Header(state, profile, viewModel::uploadPhoto)
                state.message?.let {
                    Text(it, color = if (state.messageIsError) MaterialTheme.colorScheme.error else VerdeGanancia)
                }
                val form = state.form
                if (form == null) {
                    Details(profile, editable = !state.busy, onEdit = viewModel::startEdit)
                    Links(links)
                } else {
                    EditForm(form, state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun Header(state: ProfileUiState, profile: Profile, onPhoto: (String) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhoto(uri.toString())
    }
    val avatarUrl = state.form?.avatarUrl ?: profile.avatarUrl
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Avatar(avatarUrl, state.photoUrl.takeIf { avatarUrl == profile.avatarUrl }, size = 88.dp)
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                profile.nickname ?: profile.email,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !state.busy,
            ) { Text(if (state.uploading) "Subiendo…" else "Subir foto") }
        }
    }
}

@Composable
private fun Details(profile: Profile, editable: Boolean, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Detail("Apodo", profile.nickname)
            Detail("Nombres", profile.firstName)
            Detail("Apellidos", profile.lastName)
            Detail("Teléfono", profile.phone)
            HorizontalDivider()
            Detail("Correo electrónico", profile.email)
            Detail("DNI", profile.dni)
            Text(
                "El correo y el DNI no se pueden cambiar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton("Editar perfil", onEdit, enabled = editable)
        }
    }
}

@Composable
private fun Detail(label: String, value: String?) {
    Column {
        Text(
            label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value?.ifEmpty { null } ?: "Sin completar")
    }
}

@Composable
private fun Links(links: List<ProfileLink>) {
    Card(Modifier.fillMaxWidth()) {
        links.forEach { link ->
            Card(onClick = link.onClick) {
                ListItem(
                    headlineContent = { Text(link.label) },
                    leadingContent = {
                        Icon(
                            painterResource(link.icon), contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun EditForm(form: ProfileForm, state: ProfileUiState, viewModel: ProfileViewModel) {
    val errors = state.fieldErrors
    val enabled = !state.busy
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Elige un avatar", fontWeight = FontWeight.Bold)
            AvatarPicker(form.avatarUrl, enabled, viewModel::onAvatar)
            errors["avatar_url"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            FormField(form.nickname, viewModel::onNickname, "Apodo", errors["nickname"], enabled, KeyboardType.Ascii)
            FormField(form.firstName, viewModel::onFirstName, "Nombres", errors["first_name"], enabled, KeyboardType.Text)
            FormField(form.lastName, viewModel::onLastName, "Apellidos", errors["last_name"], enabled, KeyboardType.Text)
            FormField(
                form.phone, viewModel::onPhone, "Teléfono", errors["phone"], enabled, KeyboardType.Phone,
                imeAction = ImeAction.Done, onDone = viewModel::save,
            )
            PrimaryButton("Guardar cambios", viewModel::save, enabled = enabled, loading = state.saving)
            TextButton(
                onClick = viewModel::cancelEdit, enabled = enabled, modifier = Modifier.fillMaxWidth(),
            ) { Text("Cancelar") }
        }
    }
}

@Composable
private fun AvatarPicker(selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        PresetAvatars.keys.forEachIndexed { index, avatarUrl ->
            val border = if (avatarUrl == selected) MaterialTheme.colorScheme.primary else Color.Transparent
            Avatar(
                avatarUrl, photoUrl = null, size = 44.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .selectable(avatarUrl == selected, enabled, Role.RadioButton) { onSelect(avatarUrl) }
                    .semantics { contentDescription = "Avatar ${index + 1}" }
                    .border(3.dp, border, CircleShape)
                    .padding(3.dp),
            )
        }
    }
}
