package com.moji.offline.feature.article

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.data.db.UserDataRepository
import com.moji.offline.data.word.WordDict
import com.moji.offline.data.word.WordEntry
import com.moji.offline.ui.interaction.clickableNoRipple
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.time.Clock
import kotlinx.coroutines.launch

/**
 * Word popup of the article page, ported from the original dark word card
 * (`MojiWordDetailWebView` dialog, base.apk 3.4.9): a #1B1B1D rounded-16 card
 * pinned above the audio bar with `spell|reading accent`, a part-of-speech row
 * carrying the favorite star and the speaker, the dictionary gloss and a
 * detail / MJ-dictionary button row.
 *
 * Behaviour matched on device 2026-10-17: tapping read / favorite / copy /
 * detail / dictionary keeps the card open; only the cross or a tap outside
 * closes it.
 */
private val CardBg = Color(0xFF1B1B1D)
private val CardFg = Color(0xFFF5F5F5)
private val CardAccent = Color(0xFFE4405F)
private val CardUnderline = Color(0xFF5BC8F5)
private val CardDivider = Color(0xFF3A3A3A)

@Composable
fun WordCardOverlay(
    word: WordInfo,
    articleId: String = "",
    notedWordIds: MutableSet<String> = mutableSetOf(),
    onRead: () -> Unit,
    onCopy: () -> Unit,
    onDetail: () -> Unit,
    onDictionary: () -> Unit,
    onDismiss: () -> Unit,
) {
    var entry by remember(word) { mutableStateOf<WordEntry?>(null) }
    LaunchedEffect(word) {
        // 关联词带 objectId：多音词同形不同条，必须按 id 取，不能只按拼写。
        entry = if (word.id.isNotBlank()) {
            WordDict.findById(word.id) ?: WordDict.find(word.surface, word.lemma)
        } else {
            WordDict.find(word.surface, word.lemma)
        }
    }
    // 查到词条 id 才记笔记，对位搜索成功后 word.getPk() → readNote-add。
    // 本篇当次已记过的 id 不再写，和原版 ViewModel 的 LinkedHashSet 一样。
    LaunchedEffect(articleId, entry?.id) {
        val found = entry ?: return@LaunchedEffect
        val wordId = found.id.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (articleId.isBlank()) return@LaunchedEffect
        if (!notedWordIds.add(wordId)) return@LaunchedEffect
        UserDataRepository.addWordReadingNote(
            articleId = articleId,
            wordId = wordId,
            title = found.spell.ifBlank { word.surface },
            excerpt = found.excerpt,
        )
    }
    var favorite by remember(word) { mutableStateOf(false) }
    LaunchedEffect(word, entry) {
        favorite = UserDataRepository.isWordFavorite(favoriteKey(entry, word))
    }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        // Transparent tap-catcher: the original dims nothing behind the card.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        WordCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            word = word,
            entry = entry,
            favorite = favorite,
            onToggleFavorite = {
                val next = !favorite
                favorite = next
                scope.launch {
                    val key = favoriteKey(entry, word)
                    if (next) {
                        UserDataRepository.addWordFavorite(
                            wordId = key,
                            spell = entry?.spell?.takeIf { it.isNotBlank() } ?: word.surface,
                            pron = entry?.pron ?: word.reading,
                            excerpt = entry?.excerpt.orEmpty(),
                            nowMs = Clock.System.now().toEpochMilliseconds(),
                        )
                    } else {
                        UserDataRepository.removeWordFavorite(key)
                    }
                }
            },
            onRead = onRead,
            onCopy = onCopy,
            onDetail = onDetail,
            onDictionary = onDictionary,
            onDismiss = onDismiss,
        )
    }
}

private fun favoriteKey(entry: WordEntry?, word: WordInfo): String =
    entry?.id?.takeIf { it.isNotBlank() } ?: word.surface

@Composable
private fun WordCard(
    modifier: Modifier = Modifier,
    word: WordInfo,
    entry: WordEntry?,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onRead: () -> Unit,
    onCopy: () -> Unit,
    onDetail: () -> Unit,
    onDictionary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val head = entry?.spell?.takeIf { it.isNotBlank() } ?: word.surface
    val reading = entry?.pron?.takeIf { it.isNotBlank() } ?: word.reading
    val accent = entry?.accent.orEmpty()
    val pos = entry?.pos.orEmpty()
    val gloss = entry?.definitionJa?.takeIf { it.isNotBlank() }
        ?: entry?.meaningZh?.takeIf { it.isNotBlank() }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, end = 10.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                Modifier.size(34.dp).clickableNoRipple(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { CloseGlyph(CardFg, 16.dp) }
        }
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(head, color = CardFg, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            if (reading.isNotBlank()) {
                Text("｜", color = CardFg.copy(alpha = 0.7f), fontSize = 22.sp)
                Text(reading, color = CardFg, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
            if (accent.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(accent, color = CardFg, fontSize = 20.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pos.isNotBlank()) {
                Text(pos, color = CardFg, fontSize = 15.sp, modifier = Modifier.posUnderline(CardUnderline))
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(34.dp).clickableNoRipple(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center,
            ) { StarGlyph(CardAccent, filled = favorite, size = 24.dp) }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(34.dp).clickableNoRipple(onClick = onRead),
                contentAlignment = Alignment.Center,
            ) { SpeakerGlyph(CardAccent, 24.dp) }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(34.dp).clickableNoRipple(onClick = onCopy),
                contentAlignment = Alignment.Center,
            ) { CopyGlyph(CardFg.copy(alpha = 0.9f), 20.dp) }
        }
        if (gloss != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                gloss,
                color = CardFg.copy(alpha = 0.95f),
                fontSize = 16.sp,
                lineHeight = 26.sp,
                maxLines = 3,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(CardDivider))
        Row(Modifier.fillMaxWidth().height(62.dp)) {
            CardButton(label = "详情", glyph = { DetailGlyph(CardFg, 20.dp) }, onClick = onDetail)
            Box(Modifier.width(1.dp).fillMaxHeight().background(CardDivider))
            CardButton(label = "MJ辞書", glyph = { SearchGlyph(CardFg, 20.dp) }, onClick = onDictionary)
        }
    }
}

@Composable
private fun RowScope.CardButton(label: String, glyph: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .weight(1f)
            .fillMaxSize()
            .clickableNoRipple(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        glyph()
        Spacer(Modifier.width(8.dp))
        Text(label, color = CardFg, fontSize = 17.sp)
    }
}

/** 3dp light-blue rule under the part-of-speech tag, as in the original card. */
private fun Modifier.posUnderline(color: Color): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()
        val height = 3.dp.toPx()
        drawRect(color, topLeft = Offset(0f, size.height - height), size = Size(size.width, height))
    }
)

@Composable
private fun CloseGlyph(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(0f, 0f), Offset(s, s), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(color, Offset(s, 0f), Offset(0f, s), strokeWidth = stroke.width, cap = stroke.cap)
    }
}

@Composable
private fun StarGlyph(color: Color, filled: Boolean, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val r = s / 2f
        val c = Offset(s / 2f, s / 2f)
        val path = Path()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r else r * 0.44f
            val angle = (-90.0 + i * 36.0) * PI / 180.0
            val p = Offset(c.x + radius * cos(angle).toFloat(), c.y + radius * sin(angle).toFloat())
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        if (filled) {
            drawPath(path, color)
        } else {
            drawPath(path, color, style = Stroke(width = 1.8.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun SpeakerGlyph(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val cy = s / 2f
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color, radius = s * 0.08f, center = Offset(s * 0.26f, cy))
        for (radius in listOf(0.22f, 0.40f)) {
            val path = Path()
            path.arcTo(
                rect = Rect(
                    left = s * 0.26f - s * radius,
                    top = cy - s * radius,
                    right = s * 0.26f + s * radius,
                    bottom = cy + s * radius,
                ),
                startAngleDegrees = -52f,
                sweepAngleDegrees = 104f,
                forceMoveTo = true,
            )
            drawPath(path, color, style = stroke)
        }
    }
}

@Composable
private fun CopyGlyph(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val stroke = Stroke(width = 1.7.dp.toPx(), join = StrokeJoin.Round)
        val corner = 2.dp.toPx()
        for (rect in listOf(
            Rect(s * 0.30f, 0f, s, s * 0.70f),
            Rect(0f, s * 0.30f, s * 0.70f, s),
        )) {
            drawRoundRect(
                color,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(corner, corner),
                style = stroke,
            )
        }
    }
}

@Composable
private fun DetailGlyph(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val stroke = Stroke(width = 1.7.dp.toPx(), join = StrokeJoin.Round)
        drawRoundRect(
            color,
            topLeft = Offset.Zero,
            size = Size(s, s),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = stroke,
        )
        val cap = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(s * 0.26f, s * 0.36f), Offset(s * 0.74f, s * 0.36f), strokeWidth = cap.width, cap = cap.cap)
        drawLine(color, Offset(s * 0.26f, s * 0.62f), Offset(s * 0.60f, s * 0.62f), strokeWidth = cap.width, cap = cap.cap)
    }
}

@Composable
private fun SearchGlyph(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color, radius = s * 0.30f, center = Offset(s * 0.40f, s * 0.40f), style = stroke)
        drawLine(color, Offset(s * 0.62f, s * 0.62f), Offset(s * 0.90f, s * 0.90f), strokeWidth = stroke.width, cap = stroke.cap)
    }
}
