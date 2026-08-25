package com.minidex.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minidex.app.domain.model.KeyboardPage
import com.minidex.app.ui.theme.LocalMiniDexColors

@Composable
fun PageBar(
    currentPage: KeyboardPage,
    onPageSelected: (KeyboardPage) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMiniDexColors.current
    val pages = KeyboardPage.entries

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pages.forEach { page ->
            val isSelected = page == currentPage
            val shape = RoundedCornerShape(6.dp)

            Box(
                modifier = Modifier
                    .weight(if (page == KeyboardPage.SETTINGS) 0.8f else 1.0f)
                    .height(26.dp)
                    .clip(shape)
                    .background(
                        if (isSelected) colors.accent.copy(alpha = 0.2f) else colors.surfaceElevated,
                        shape
                    )
                    .border(
                        1.dp,
                        if (isSelected) colors.accent else colors.border.copy(alpha = 0.4f),
                        shape
                    )
                    .clickable { onPageSelected(page) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (page == KeyboardPage.SETTINGS) "⚙" else page.title,
                    color = if (isSelected) colors.accent else colors.textSecondary,
                    fontSize = if (page == KeyboardPage.SETTINGS) 13.sp else 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}
