package com.qtekfun.ultimatephone.feature.data

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.data.BusinessCategories

/** Material icon for an icon name from [BusinessCategories]. Unknown names get a generic shop. */
fun businessIcon(iconName: String?): ImageVector = when (iconName) {
    "restaurant" -> Icons.Filled.Restaurant
    "shopping_basket" -> Icons.Filled.ShoppingBasket
    "shopping_bag" -> Icons.Filled.ShoppingBag
    "local_hospital" -> Icons.Filled.LocalHospital
    "hotel" -> Icons.Filled.Hotel
    "directions_car" -> Icons.Filled.DirectionsCar
    "account_balance" -> Icons.Filled.AccountBalance
    "content_cut" -> Icons.Filled.ContentCut
    "build" -> Icons.Filled.Build
    "business_center" -> Icons.Filled.BusinessCenter
    "school" -> Icons.Filled.School
    "account_balance_wallet" -> Icons.Filled.AccountBalanceWallet
    "local_taxi" -> Icons.Filled.LocalTaxi
    "sports_tennis" -> Icons.Filled.SportsTennis
    "theater_comedy" -> Icons.Filled.TheaterComedy
    else -> Icons.Filled.Storefront
}

/** The localised name of a category id; "Other" for anything unknown. */
@StringRes
fun businessCategoryLabel(category: String?): Int = when (category) {
    "food" -> R.string.data_category_food
    "grocery" -> R.string.data_category_grocery
    "shopping" -> R.string.data_category_shopping
    "health" -> R.string.data_category_health
    "lodging" -> R.string.data_category_lodging
    "automotive" -> R.string.data_category_automotive
    "finance" -> R.string.data_category_finance
    "beauty" -> R.string.data_category_beauty
    "services" -> R.string.data_category_services
    "office" -> R.string.data_category_office
    "education" -> R.string.data_category_education
    "government" -> R.string.data_category_government
    "transport" -> R.string.data_category_transport
    "leisure" -> R.string.data_category_leisure
    "culture" -> R.string.data_category_culture
    else -> R.string.data_category_other
}

/** "[icon] Restaurant": the category line shown under a business name. */
@Composable
fun BusinessCategoryLine(category: String?, iconName: String?, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(businessIcon(iconName), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            text = stringResource(businessCategoryLabel(category)),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** Round avatar of a business: its category icon instead of initials. */
@Composable
fun BusinessAvatar(iconName: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
        Icon(businessIcon(iconName), contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(size / 2))
    }
}
