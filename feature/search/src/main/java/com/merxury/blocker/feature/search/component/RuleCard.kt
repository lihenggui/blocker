package com.merxury.blocker.feature.search.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.database.generalrule.GeneralRuleEntity
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.search.R.string

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleCard(item: GeneralRuleEntity) {
    ElevatedCard(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        onClick = {}
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            RuleBasicInfo(item = item)
            RuleDetail(item = item)
        }
    }
}

@Composable
fun RuleBasicInfo(
    item: GeneralRuleEntity
) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.iconUrl == null) {
            Icon(
                painter = painterResource(id = BlockerIcons.Android),
                contentDescription = stringResource(id = string.rule_icon),
                modifier = Modifier.size(48.dp)
            )
        } else {
            //TODO
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            item.name?.let { Text(text = it, style = MaterialTheme.typography.titleMedium) }
            Spacer(modifier = Modifier.height(2.dp))
            item.company?.let { Text(text = it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
fun RuleDetail(item: GeneralRuleEntity) {
    val ruleDetail = listOf(
        RuleDetailItemInfo("Rules", item.name),
        RuleDetailItemInfo("Description", item.description),
        RuleDetailItemInfo("Side effect", item.sideEffect),
        RuleDetailItemInfo("Safe to block", item.safeToBlock.toString()),
        RuleDetailItemInfo("Contributor", item.contributors.toString()),
    )
    ruleDetail.forEach {
        RuleDetailItem(
            title = it.title,
            detail = it.detail
        )
    }
}

@Composable
fun RuleDetailItem(
    title: String?,
    detail: String?
) {
    Column(modifier = Modifier.padding(bottom = 2.dp)) {
        title?.let { Text(text = it, style = MaterialTheme.typography.titleSmall) }
        detail?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
    }
}

private data class RuleDetailItemInfo(
    val title: String?,
    val detail: String?
)

@Composable
@Preview
fun RuleBasicInfoPreview() {
    val item = GeneralRuleEntity(
        id = 100,
        name = "Blocker",
        iconUrl = null,
        company = "Merxury blocker",
        description = "Merxury Merxury Merxury Merxury Merxury Merxury Merxury Merxury",
        sideEffect = "unknown",
        safeToBlock = true,
        contributors = listOf("blocker")
    )
    BlockerTheme {
        RuleCard(item = item)
    }
}
