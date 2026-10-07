package dev.novqigarrix.mymusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.ui.theme.Sizes

/** The small circular profile badge in Spotify's top bars. */
@Composable
fun ProfileAvatar(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.library_profile)
    Box(
        modifier = modifier
            .size(Sizes.avatar)
            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name).take(1),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
