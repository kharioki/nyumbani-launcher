package dev.kharioki.nyumbani

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.floor

/** A QR code drawn as crisp squares on a white card (phones need the light quiet zone). */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
            )
        }.getOrNull()
    }
    Canvas(
        modifier
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        val m = matrix ?: return@Canvas
        // Whole-pixel modules, centred, so the code never blurs or shows seams.
        val cell = floor(size.minDimension / m.width).coerceAtLeast(1f)
        val origin = Offset((size.width - cell * m.width) / 2f, (size.height - cell * m.height) / 2f)
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m[x, y]) drawRect(Night, topLeft = origin + Offset(x * cell, y * cell), size = Size(cell, cell))
        }
    }
}
