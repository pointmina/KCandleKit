package com.hanto.kcandlekit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hanto.kcandlekit.compose.DrawingTool

private val AMBER = Color(0xFFFFC107)
private val FAB_BG = Color(0xFF1E2130)

@Composable
fun DrawingFab(
    activeTool: DrawingTool,
    onToolSelected: (DrawingTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 서브 메뉴 — 연필 버튼 위에 슬라이드
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit  = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SubToolButton(
                    icon        = Icons.Default.HorizontalRule,
                    label       = "수평선",
                    isActive    = activeTool == DrawingTool.HORIZONTAL,
                    onClick     = {
                        onToolSelected(DrawingTool.HORIZONTAL)
                        expanded = false
                    },
                )
                SubToolButton(
                    icon        = Icons.AutoMirrored.Filled.ShowChart,
                    label       = "추세선",
                    isActive    = activeTool == DrawingTool.TREND_LINE,
                    onClick     = {
                        onToolSelected(DrawingTool.TREND_LINE)
                        expanded = false
                    },
                )
            }
        }

        // 연필 메인 버튼
        val pencilActive = activeTool != DrawingTool.NONE
        IconButton(
            onClick = {
                if (pencilActive) {
                    onToolSelected(DrawingTool.NONE)
                    expanded = false
                } else {
                    expanded = !expanded
                }
            },
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = if (pencilActive) AMBER else FAB_BG,
                    shape = CircleShape,
                ),
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "그리기 툴",
                tint = if (pencilActive) Color.Black else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SubToolButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isActive) AMBER else FAB_BG,
        modifier = Modifier.padding(bottom = 6.dp).size(32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Color.Black else Color.White,
            modifier = Modifier
                .padding(6.dp)
                .size(20.dp),
        )
    }
}
