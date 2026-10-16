package com.moji.offline.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.offline.data.catalog.ReaderBook
import com.moji.offline.data.catalog.ReaderCatalog
import com.moji.offline.data.catalog.ReaderCatalogData
import com.moji.offline.ui.i18n.LocalAppStrings
import com.moji.offline.ui.interaction.clickableNoRipple
import com.moji.offline.ui.theme.MojiPink
import com.moji.offline.resources.Res
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap

private enum class ShelfTab { All, Mine, Official }

private sealed class ShelfItem {
    data object Upload : ShelfItem()
    data class Book(val book: ReaderBook) : ShelfItem()
}

@Composable
fun ReaderRoute(
    onOpenColumn: (String) -> Unit = {},
    onOpenArticle: (planId: String, topicId: String) -> Unit = { _, _ -> },
) {
    val strings = LocalAppStrings.current
    val colors = MaterialTheme.colorScheme
    var catalog by remember { mutableStateOf<ReaderCatalogData?>(null) }
    var tab by rememberSaveable { mutableStateOf(ShelfTab.All.name) }
    val shelfTab = runCatching { ShelfTab.valueOf(tab) }.getOrDefault(ShelfTab.All)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        catalog = ReaderCatalog.load()
    }

    val books = catalog?.books.orEmpty()
    val items: List<ShelfItem> = remember(books, shelfTab) {
        val official = books.filter { it.type.equals("office", ignoreCase = true) }
        val mine = books.filter { !it.type.equals("office", ignoreCase = true) }
        when (shelfTab) {
            ShelfTab.All -> listOf(ShelfItem.Upload) + official.map { ShelfItem.Book(it) } + mine.map { ShelfItem.Book(it) }
            ShelfTab.Mine -> listOf(ShelfItem.Upload) + mine.map { ShelfItem.Book(it) }
            ShelfTab.Official -> official.map { ShelfItem.Book(it) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Centered title — no VIP / membership affordance.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    strings.readerTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground,
                )
            }

            ShelfTabs(
                selected = shelfTab,
                onSelect = { tab = it.name },
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items, key = {
                    when (it) {
                        ShelfItem.Upload -> "upload"
                        is ShelfItem.Book -> it.book.id
                    }
                }) { item ->
                    when (item) {
                        ShelfItem.Upload -> UploadTile(
                            onClick = {
                                scope.launch { snackbar.showSnackbar(strings.readerUploadUnavailable) }
                            },
                        )
                        is ShelfItem.Book -> BookTile(
                            book = item.book,
                            onClick = {
                                scope.launch { snackbar.showSnackbar(strings.readerBookUnavailable) }
                            },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                scope.launch { snackbar.showSnackbar(strings.readerUploadUnavailable) }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            shape = CircleShape,
            containerColor = MojiPink,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(4.dp, 6.dp),
        ) {
            Text("+", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        )
    }
}

@Composable
private fun ShelfTabs(selected: ShelfTab, onSelect: (ShelfTab) -> Unit) {
    val strings = LocalAppStrings.current
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        listOf(
            ShelfTab.All to strings.readerTabAll,
            ShelfTab.Mine to strings.readerTabMine,
            ShelfTab.Official to strings.readerTabOfficial,
        ).forEach { (value, label) ->
            val active = selected == value
            Column(
                modifier = Modifier
                    .clickableNoRipple { onSelect(value) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) colors.onBackground else colors.onBackground.copy(alpha = 0.45f),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(width = 18.dp, height = 3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (active) MojiPink else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun UploadTile(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val strings = LocalAppStrings.current
    val dash = colors.onSurface.copy(alpha = 0.28f)
    Column(
        modifier = Modifier.clickableNoRipple(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.35f))
                .border(1.dp, dash, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", fontSize = 26.sp, color = dash, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Text(
                    strings.readerUpload,
                    fontSize = 11.sp,
                    color = dash,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Reserve title space so grid rows align with book tiles.
        Text("", fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun BookTile(book: ReaderBook, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.clickableNoRipple(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BookCover(book = book)
        Spacer(Modifier.height(8.dp))
        Text(
            book.title,
            fontSize = 12.sp,
            color = colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Real OSS cover when bundled (files/book_covers/{id}.jpg), stylized fallback otherwise. */
@Composable
private fun BookCover(book: ReaderBook) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, book.id) {
        value = runCatching {
            Res.readBytes("files/book_covers/${book.id}.jpg").decodeToImageBitmap()
        }.getOrNull()
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = book.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop,
        )
        return
    }
    StylizedCover(title = book.title, seed = book.coverId.ifBlank { book.id })
}

/** Stylized cover when OSS cover images are not bundled. */
@Composable
private fun StylizedCover(title: String, seed: String) {
    val palette = coverPalette(seed)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(4.dp))
            .background(palette.bg)
            .border(0.5.dp, Color(0x14000000), RoundedCornerShape(4.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Soft accent wash (MOJi covers are mostly white with a small illustration).
            drawCircle(
                color = palette.accent.copy(alpha = 0.18f),
                radius = size.minDimension * 0.35f,
                center = Offset(size.width * 0.72f, size.height * 0.28f),
            )
            drawCircle(
                color = palette.accent.copy(alpha = 0.12f),
                radius = size.minDimension * 0.22f,
                center = Offset(size.width * 0.28f, size.height * 0.78f),
            )
            // Thin frame line like print covers.
            drawRect(
                color = palette.accent.copy(alpha = 0.35f),
                topLeft = Offset(size.width * 0.1f, size.height * 0.1f),
                size = Size(size.width * 0.8f, size.height * 0.8f),
                style = Stroke(width = 1.dp.toPx()),
            )
            // Decorative mark.
            val path = Path().apply {
                moveTo(size.width * 0.55f, size.height * 0.55f)
                quadraticTo(
                    size.width * 0.75f,
                    size.height * 0.45f,
                    size.width * 0.7f,
                    size.height * 0.7f,
                )
            }
            drawPath(path, palette.accent.copy(alpha = 0.55f), style = Stroke(width = 2.dp.toPx()))
        }
        Text(
            title,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp, end = 10.dp)
                .fillMaxWidth(0.55f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = palette.ink,
            lineHeight = 18.sp,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class CoverPalette(val bg: Color, val accent: Color, val ink: Color)

private fun coverPalette(seed: String): CoverPalette {
    val accents = listOf(
        Color(0xFFE8A0A0),
        Color(0xFF9DB8D4),
        Color(0xFFB8C9A0),
        Color(0xFFD4B896),
        Color(0xFFC9B0D4),
        Color(0xFFA0C9C4),
    )
    val idx = (seed.hashCode().and(0x7fffffff)) % accents.size
    return CoverPalette(
        bg = Color(0xFFF7F5F2),
        accent = accents[idx],
        ink = Color(0xFF2C2C2E),
    )
}
