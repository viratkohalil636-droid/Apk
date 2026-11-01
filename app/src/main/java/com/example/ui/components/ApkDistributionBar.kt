package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CategoryBreakdown
import com.example.model.CategoryType
import com.example.parser.ApkParser
import com.example.ui.theme.ColorAssets
import com.example.ui.theme.ColorDex
import com.example.ui.theme.ColorMeta
import com.example.ui.theme.ColorNative
import com.example.ui.theme.ColorOther
import com.example.ui.theme.ColorRes

fun getCategoryColor(type: CategoryType): Color {
    return when (type) {
        CategoryType.DEX -> ColorDex
        CategoryType.NATIVE_LIB -> ColorNative
        CategoryType.RESOURCES -> ColorRes
        CategoryType.ASSETS -> ColorAssets
        CategoryType.META_SIGNATURES -> ColorMeta
        CategoryType.OTHER -> ColorOther
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApkDistributionBar(
    categories: List<CategoryBreakdown>,
    selectedCategory: CategoryType? = null,
    onCategoryClick: ((CategoryType) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("apk_distribution_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "APK Storage Map",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Visual File Map",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Segmented Distribution Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val visibleCats = categories.filter { it.percentage > 0.05f }
                    if (visibleCats.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .background(ColorOther)
                        )
                    } else {
                        visibleCats.forEach { cat ->
                            val color = getCategoryColor(cat.type)
                            val isSelected = selectedCategory == cat.type
                            Box(
                                modifier = Modifier
                                    .weight(cat.percentage.coerceAtLeast(1f))
                                    .fillMaxHeight()
                                    .background(if (selectedCategory != null && !isSelected) color.copy(alpha = 0.35f) else color)
                                    .clickable(enabled = onCategoryClick != null) {
                                        onCategoryClick?.invoke(cat.type)
                                    }
                            )
                        }
                    }
                }
            }

            // Legend FlowRow
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val color = getCategoryColor(cat.type)
                    val isSelected = selectedCategory == cat.type

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, color) else null,
                        modifier = Modifier
                            .animateContentSize()
                            .clickable(enabled = onCategoryClick != null) {
                                onCategoryClick?.invoke(cat.type)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = cat.label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = String.format("%.1f%%", cat.percentage),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = color,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "(${ApkParser.formatBytes(cat.compressedBytes)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
