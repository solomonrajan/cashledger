/*
 * Copyright (c) 2018.
 * Copyright (c) 2026 solomonrajan.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.ui.view.drawer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.view.theme.ITheme

private val GoogleSansFontFamily = FontFamily(Font(R.font.google_sans))

/**
 * Material 3 Jetpack Compose Navigation Drawer Header.
 * Displays the current active wallet name, balance, and an expand chevron to toggle the wallet list.
 */
@Composable
fun NavigationDrawerHeader(
    walletName: String,
    walletMoney: String,
    isExpanded: Boolean,
    theme: ITheme,
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "ChevronRotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onHeaderClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = walletName,
                    fontFamily = GoogleSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(theme.drawerTextColor)
                )
                Text(
                    text = walletMoney,
                    fontFamily = GoogleSansFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(theme.textColorSecondary),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_expand_more_black_24dp),
                contentDescription = stringResource(id = R.string.description_navigation_drawer_open),
                tint = Color(theme.drawerIconColor),
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation)
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 28.dp),
            thickness = 1.dp,
            color = Color(theme.drawerTextColor).copy(alpha = 0.12f)
        )
    }
}
