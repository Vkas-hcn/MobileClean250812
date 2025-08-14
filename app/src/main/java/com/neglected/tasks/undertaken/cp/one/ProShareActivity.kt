package com.neglected.tasks.undertaken.cp.one

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.neglected.tasks.undertaken.R

class ProShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ProShareScreen(
                onBackClick = { finish() },
                onShareClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=${packageName}")
                    }
                    try {
                        startActivity(Intent.createChooser(intent, "Share via"))
                    } catch (ex: Exception) {
                        // Handle error
                    }
                },
                onPrivacyPolicyClick = {
                    // TODO
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = "https://www.google.com".toUri()
                    }
                    startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun ProShareScreen(
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit
) {
    // Roboto Condensed字体
    val robotoCondensed = FontFamily(
        Font(R.font.roboto_condensed)
    )

    val gradientBackground = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF03170B), // startColor
            Color(0xFF03180B)  // endColor
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_back),
                contentDescription = "Back",
                modifier = Modifier
                    .clickable { onBackClick() }
                    .padding(8.dp)
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontFamily = robotoCondensed,
                    modifier = Modifier.padding(end = 48.dp) // 补偿左侧返回按钮的空间
                )
            }
        }

        // Logo
        Image(
            painter = painterResource(id = R.mipmap.logo),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 48.dp)
        )

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // Share 项
            SettingsItem(
                iconRes = R.mipmap.ic_share,
                text = "Share",
                onClick = onShareClick,
                fontFamily = robotoCondensed
            )

            Spacer(modifier = Modifier.height(20.dp))

            SettingsItem(
                iconRes = R.mipmap.ic_popr,
                text = "Privacy Policy",
                onClick = onPrivacyPolicyClick,
                fontFamily = robotoCondensed
            )
        }
    }
}

@Composable
fun SettingsItem(
    iconRes: Int,
    text: String,
    onClick: () -> Unit,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        // 背景图片
        Image(
            painter = painterResource(id = R.mipmap.bg_item_set),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )

        // 内容
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧图标
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp) // 根据实际图标大小调整
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 文本
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                fontFamily = fontFamily,
                modifier = Modifier.weight(1f)
            )

            // 右侧箭头
            Image(
                painter = painterResource(id = R.mipmap.ic_vector),
                contentDescription = null,
                modifier = Modifier.size(16.dp) // 根据实际箭头大小调整
            )
        }
    }
}