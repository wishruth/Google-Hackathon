package app.npusensei.vision

import android.content.Context
import android.util.Log

object EfficientDetLabelProvider {
    private const val TAG = "EfficientDetLabels"

    fun loadLabels(
        context: Context,
        labelAssetName: String = "labels.txt",
    ): List<String> = runCatching {
        context.assets.open(labelAssetName).bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }.getOrElse { throwable ->
        Log.w(TAG, "$labelAssetName missing; falling back to class ids", throwable)
        emptyList()
    }
}
