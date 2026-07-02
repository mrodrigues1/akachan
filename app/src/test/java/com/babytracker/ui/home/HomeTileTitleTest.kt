package com.babytracker.ui.home

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.babytracker.ui.theme.BabyTrackerTheme
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class HomeTileTitleTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `long title wraps without ellipsis`() {
        val title = "Histórico de mamadas"

        setTile(title = title, width = 150)

        val result = layoutResultFor(title)
        assertTrue(result.lineCount <= 2)
        assertFalse(result.hasVisualOverflow)
        assertBreaksOnlyAtWhitespace(title, result)
    }

    @Test
    fun `word boundary detection distinguishes wrapping from splitting`() {
        assertTrue("Amamentação".isWordSplitAt(5))
        assertFalse("Histórico de mamadas".isWordSplitAt(10))
    }

    @Test
    fun `long subtitle wraps without ellipsis`() {
        val subtitle = "Capture os momentos especiais do seu bebê"
        composeRule.setContent {
            BabyTrackerTheme {
                HomeTileStatusText(
                    text = subtitle,
                    color = Color.Black,
                    modifier = Modifier.width(10.dp),
                )
            }
        }

        val result = layoutResultFor(subtitle)
        assertFalse(result.hasVisualOverflow)
    }

    @Test
    fun `feeding prediction subtitle has no line limits`() {
        val source = sequenceOf(
            File("src/main/java/com/babytracker/ui/home/HomeScreen.kt"),
            File("app/src/main/java/com/babytracker/ui/home/HomeScreen.kt"),
        ).first(File::exists).readText()
        val subtitle = source
            .substringAfter("internal fun FeedingPredictionSubtitle(")
            .substringBefore("internal fun PumpingHomeCard(")

        assertFalse(subtitle.contains("maxLines"))
        assertFalse(subtitle.contains("TextOverflow.Ellipsis"))
    }

    private fun setTile(
        title: String,
        width: Int,
    ) {
        composeRule.setContent {
            BabyTrackerTheme {
                HomeTrackerTile(
                    title = title,
                    contentDescription = title,
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    onClick = {},
                    icon = {},
                    modifier = Modifier.width(width.dp),
                ) {}
            }
        }
    }

    private fun layoutResultFor(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = composeRule
            .onNodeWithText(text, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
        check(action?.invoke(results) == true)
        return results.single()
    }

    private fun assertBreaksOnlyAtWhitespace(
        text: String,
        result: TextLayoutResult,
    ) {
        repeat(result.lineCount - 1) { line ->
            val end = result.getLineEnd(line)
            assertTrue(text[end - 1].isWhitespace() || text[end].isWhitespace())
        }
    }
}
