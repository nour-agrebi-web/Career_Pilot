package tn.careerpilot.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tn.careerpilot.domain.Validators

/** Lit le fichier choisi dans le sélecteur Android (hors du thread principal). */
class UriFileReader(private val resolver: ContentResolver) {

    suspend fun read(uri: Uri): PickedFile? = withContext(Dispatchers.IO) {
        runCatching {
            var name = "cv"
            var size = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: name
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
            // Fichier trop gros : inutile de le charger en mémoire, la règle des 5 Mo le refusera.
            val bytes = if (size > Validators.MAX_CV_SIZE) {
                ByteArray(0)
            } else {
                resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            }
            PickedFile(name, resolver.getType(uri), bytes, if (size >= 0) size else bytes.size.toLong())
        }.getOrNull()
    }
}
