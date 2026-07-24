package com.winds.bledebugger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class MarkdownBlockType {
    Heading1,
    Heading2,
    Heading3,
    Bullet,
    Ordered,
    Quote,
    Code,
    Divider,
    Paragraph
}

private data class MarkdownBlock(
    val type: MarkdownBlockType,
    val text: String = "",
    val marker: String = ""
)

@Composable
fun MarkdownReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val primaryColor = MiuixTheme.colorScheme.primary
    val codeBackground = MiuixTheme.colorScheme.secondaryContainer
    val secondaryTextColor = MiuixTheme.colorScheme.onSurfaceVariantSummary

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block.type) {
                MarkdownBlockType.Heading1 -> Text(
                    text = inlineMarkdown(block.text, primaryColor, codeBackground),
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold
                )

                MarkdownBlockType.Heading2 -> Text(
                    text = inlineMarkdown(block.text, primaryColor, codeBackground),
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Bold
                )

                MarkdownBlockType.Heading3 -> Text(
                    text = inlineMarkdown(block.text, primaryColor, codeBackground),
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold
                )

                MarkdownBlockType.Bullet,
                MarkdownBlockType.Ordered -> Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (block.type == MarkdownBlockType.Bullet) "•" else block.marker,
                        color = primaryColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = inlineMarkdown(block.text, primaryColor, codeBackground),
                        modifier = Modifier.weight(1f),
                        style = MiuixTheme.textStyles.body1
                    )
                }

                MarkdownBlockType.Quote -> Text(
                    text = inlineMarkdown(block.text, primaryColor, codeBackground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(codeBackground)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = secondaryTextColor,
                    fontStyle = FontStyle.Italic,
                    style = MiuixTheme.textStyles.body1
                )

                MarkdownBlockType.Code -> Text(
                    text = block.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(codeBackground)
                        .padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MiuixTheme.textStyles.body2
                )

                MarkdownBlockType.Divider -> Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(secondaryTextColor.copy(alpha = 0.35f))
                        .padding(top = 1.dp)
                )

                MarkdownBlockType.Paragraph -> Text(
                    text = inlineMarkdown(block.text, primaryColor, codeBackground),
                    style = MiuixTheme.textStyles.body1
                )
            }
        }
    }
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    var inCodeBlock = false

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock(MarkdownBlockType.Paragraph, paragraph.joinToString(" "))
            paragraph.clear()
        }
    }

    fun flushCode() {
        blocks += MarkdownBlock(MarkdownBlockType.Code, code.joinToString("\n"))
        code.clear()
    }

    markdown.replace("\r\n", "\n").lineSequence().forEach { sourceLine ->
        val line = sourceLine.trimEnd()
        if (line.trimStart().startsWith("```")) {
            flushParagraph()
            if (inCodeBlock) flushCode()
            inCodeBlock = !inCodeBlock
            return@forEach
        }
        if (inCodeBlock) {
            code += line
            return@forEach
        }
        if (line.isBlank()) {
            flushParagraph()
            return@forEach
        }

        val trimmed = line.trimStart()
        val ordered = Regex("""^(\d+[.)])\s+(.+)$""").matchEntire(trimmed)
        val block = when {
            trimmed.matches(Regex("""^([-*_])\1\1+$""")) ->
                MarkdownBlock(MarkdownBlockType.Divider)

            trimmed.startsWith("### ") ->
                MarkdownBlock(MarkdownBlockType.Heading3, trimmed.removePrefix("### ").trim())

            trimmed.startsWith("## ") ->
                MarkdownBlock(MarkdownBlockType.Heading2, trimmed.removePrefix("## ").trim())

            trimmed.startsWith("# ") ->
                MarkdownBlock(MarkdownBlockType.Heading1, trimmed.removePrefix("# ").trim())

            trimmed.startsWith("> ") ->
                MarkdownBlock(MarkdownBlockType.Quote, trimmed.removePrefix("> ").trim())

            Regex("""^[-+*]\s+.+$""").matches(trimmed) ->
                MarkdownBlock(MarkdownBlockType.Bullet, trimmed.drop(2).trim())

            ordered != null ->
                MarkdownBlock(
                    type = MarkdownBlockType.Ordered,
                    text = ordered.groupValues[2],
                    marker = ordered.groupValues[1]
                )

            else -> null
        }

        if (block == null) {
            paragraph += trimmed
        } else {
            flushParagraph()
            blocks += block
        }
    }

    flushParagraph()
    if (inCodeBlock || code.isNotEmpty()) flushCode()
    return blocks
}

private val inlinePattern = Regex(
    """\[([^\]]+)]\((https?://[^\s)]+)\)|\*\*([^*]+)\*\*|__([^_]+)__|`([^`]+)`|\*([^*]+)\*|_([^_]+)_"""
)

private fun inlineMarkdown(
    text: String,
    primaryColor: androidx.compose.ui.graphics.Color,
    codeBackground: androidx.compose.ui.graphics.Color
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    inlinePattern.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        when {
            match.groupValues[1].isNotEmpty() -> {
                val label = match.groupValues[1]
                val url = match.groupValues[2]
                withLink(LinkAnnotation.Url(url)) {
                    withStyle(
                        SpanStyle(
                            color = primaryColor,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(label)
                    }
                }
            }

            match.groupValues[3].isNotEmpty() || match.groupValues[4].isNotEmpty() -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(match.groupValues[3].ifEmpty { match.groupValues[4] })
                }
            }

            match.groupValues[5].isNotEmpty() -> {
                withStyle(
                    SpanStyle(
                        background = codeBackground,
                        fontFamily = FontFamily.Monospace
                    )
                ) {
                    append(match.groupValues[5])
                }
            }

            else -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(match.groupValues[6].ifEmpty { match.groupValues[7] })
                }
            }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}
