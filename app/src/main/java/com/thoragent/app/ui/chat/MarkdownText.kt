package com.thoragent.app.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thoragent.app.ui.theme.ThorAmber
import com.thoragent.app.ui.theme.ThorGold
import com.thoragent.app.ui.theme.ThorTextSecondary

/*
 * MarkdownText — renderer markdown ringan untuk balasan agent.
 * Mendukung: # ## ### heading, **bold**, *italic*, `kode`, ``` blok kode ```,
 * - daftar, 1. daftar bernomor, > kutipan, --- pemisah, [teks](url).
 */

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class CodeBlock(val lang: String, val code: String) : MdBlock
    data class BulletList(val items: List<String>) : MdBlock
    data class NumberList(val items: List<String>) : MdBlock
    data class Quote(val text: String) : MdBlock
    object Divider : MdBlock
}

private fun parseBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.lines()
    var i = 0
    val para = StringBuilder()

    fun flushPara() {
        val t = para.toString().trim()
        if (t.isNotEmpty()) blocks.add(MdBlock.Paragraph(t))
        para.clear()
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flushPara()
                val lang = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.appendLine(lines[i]); i++
                }
                blocks.add(MdBlock.CodeBlock(lang, code.toString().trimEnd()))
            }
            trimmed.matches(Regex("^#{1,6}\\s.+")) -> {
                flushPara()
                val level = trimmed.takeWhile { it == '#' }.length
                blocks.add(MdBlock.Heading(level, trimmed.dropWhile { it == '#' }.trim()))
            }
            trimmed == "---" || trimmed == "***" -> {
                flushPara(); blocks.add(MdBlock.Divider)
            }
            trimmed.startsWith("> ") -> {
                flushPara()
                val q = StringBuilder(trimmed.removePrefix("> "))
                while (i + 1 < lines.size && lines[i + 1].trim().startsWith("> ")) {
                    i++; q.append(' ').append(lines[i].trim().removePrefix("> "))
                }
                blocks.add(MdBlock.Quote(q.toString()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                flushPara()
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("• ")) {
                        items.add(t.substring(2)); i++
                    } else if (t.isNotEmpty() && !t.startsWith("#") && !t.startsWith("```") &&
                        lines[i].startsWith("  ") && items.isNotEmpty()
                    ) {
                        // lanjutan item terakhir (indent)
                        items[items.size - 1] = items.last() + " " + t; i++
                    } else break
                }
                blocks.add(MdBlock.BulletList(items))
            }
            trimmed.matches(Regex("^\\d+\\.\\s.+")) -> {
                flushPara()
                val items = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().matches(Regex("^\\d+\\.\\s.+"))) {
                    items.add(lines[i].trim().replace(Regex("^\\d+\\.\\s"), "")); i++
                }
                blocks.add(MdBlock.NumberList(items))
            }
            trimmed.isEmpty() -> { flushPara() }
            else -> { para.appendLine(line) }
        }
        i++
    }
    flushPara()
    return blocks
}

/** Inline: **bold**, *italic*, `kode`, [teks](url). */
private fun renderInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val s = text
    while (i < s.length) {
        when {
            s.startsWith("**", i) -> {
                val end = s.indexOf("**", i + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, end)) }
                    i = end + 2
                } else { append('*'); i++ }
            }
            s[i] == '`' -> {
                val end = s.indexOf('`', i + 1)
                if (end > 0) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            background = ThorAmber.copy(alpha = 0.14f),
                            color = ThorGold
                        )
                    ) { append(s.substring(i + 1, end)) }
                    i = end + 1
                } else { append('`'); i++ }
            }
            s[i] == '*' && i + 1 < s.length && s[i + 1] != ' ' -> {
                val end = s.indexOf('*', i + 1)
                if (end > 0 && end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.substring(i + 1, end)) }
                    i = end + 1
                } else { append('*'); i++ }
            }
            s[i] == '[' -> {
                val close = s.indexOf(']', i)
                if (close > 0 && close + 1 < s.length && s[close + 1] == '(') {
                    val end = s.indexOf(')', close + 2)
                    if (end > 0) {
                        withStyle(SpanStyle(color = ThorGold, textDecoration = TextDecoration.Underline)) {
                            append(s.substring(i + 1, close))
                        }
                        i = end + 1
                    } else { append(s[i]); i++ }
                } else { append(s[i]); i++ }
            }
            else -> { append(s[i]); i++ }
        }
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier = modifier) {
        blocks.forEach { b ->
            when (b) {
                is MdBlock.Heading -> Text(
                    renderInline(b.text),
                    color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
                is MdBlock.Paragraph -> Text(
                    renderInline(b.text),
                    color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                is MdBlock.CodeBlock -> Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF101014),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        if (b.lang.isNotBlank()) {
                            Text(
                                b.lang,
                                style = MaterialTheme.typography.labelSmall,
                                color = ThorTextSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text(
                            b.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFFE8E8EC),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        )
                    }
                }
                is MdBlock.BulletList -> b.items.forEach { item ->
                    Row(Modifier.padding(vertical = 1.dp)) {
                        Text("•  ", color = ThorGold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            renderInline(item),
                            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is MdBlock.NumberList -> b.items.forEachIndexed { n, item ->
                    Row(Modifier.padding(vertical = 1.dp)) {
                        Text("${n + 1}.  ", color = ThorGold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            renderInline(item),
                            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is MdBlock.Quote -> Surface(
                    color = ThorAmber.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 3.dp)
                ) {
                    Row {
                        Spacer(Modifier.width(3.dp).height(0.dp))
                        Surface(color = ThorAmber.copy(alpha = 0.5f), modifier = Modifier.width(3.dp).height(20.dp)) {}
                        Text(
                            renderInline(b.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ThorTextSecondary,
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp, end = 4.dp)
                        )
                    }
                }
                MdBlock.Divider -> androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}
