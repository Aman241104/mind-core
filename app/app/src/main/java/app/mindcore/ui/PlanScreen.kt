package app.mindcore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mindcore.data.Api
import app.mindcore.data.PlanAction
import app.mindcore.data.PlanMarket
import app.mindcore.data.PlanScholarship
import app.mindcore.data.PlanUniversity
import kotlinx.coroutines.launch

@Composable
fun PlanScreen(api: Api, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var countryFilter by rememberSaveable { mutableStateOf<String?>(null) }

    var actions by remember { mutableStateOf<List<PlanAction>>(emptyList()) }
    var universities by remember { mutableStateOf<List<PlanUniversity>>(emptyList()) }
    var market by remember { mutableStateOf<List<PlanMarket>>(emptyList()) }
    var scholarships by remember { mutableStateOf<List<PlanScholarship>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(api) {
        loading = true
        runCatching {
            actions = api.planActions()
            universities = api.planUniversities()
            market = api.planMarket()
            scholarships = api.planScholarships()
        }.onSuccess { error = null }.onFailure { error = it.message }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(scheme.surface)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back",
                modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(10.dp).size(26.dp),
                tint = scheme.onSurface)
            Text("MS Abroad Plan", modifier = Modifier.weight(1f).padding(start = 12.dp),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        }

        PillTabs(
            options = listOf(0 to "Actions", 1 to "Universities", 2 to "Market", 3 to "Scholarships"),
            selected = tab, onSelect = { tab = it },
            modifier = Modifier.padding(bottom = 8.dp),
        )

        when {
            loading -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text(error!!, color = scheme.error)
            }
            tab == 0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(actions, key = { it.id }) { a -> ActionCard(a, scheme) }
                item { Spacer(Modifier.height(24.dp)) }
            }
            tab == 1 -> {
                val countries = remember(universities) { universities.map { it.country }.distinct() }
                val filtered = remember(universities, countryFilter) {
                    countryFilter?.let { c -> universities.filter { it.country == c } } ?: universities
                }
                Column(Modifier.fillMaxSize()) {
                    PillTabs(
                        options = listOf<Pair<String?, String>>(null to "All") + countries.map { it to it },
                        selected = countryFilter, onSelect = { countryFilter = it },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(filtered, key = { it.id }) { u ->
                            UniversityCard(u, scheme, onToggleWishlist = {
                                universities = universities.map { if (it.id == u.id) it.copy(wishlisted = !it.wishlisted) else it }
                                scope.launch { runCatching { api.toggleWishlist(u.id) } }
                            })
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
            tab == 2 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(market, key = { it.id }) { m -> MarketCard(m, scheme) }
                item { Spacer(Modifier.height(24.dp)) }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(scholarships, key = { it.id }) { s -> ScholarshipCard(s, scheme) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ActionCard(a: PlanAction, scheme: ColorScheme) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(a.whenText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = scheme.primary)
        Text(a.action, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
        a.why?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
    }
}

@Composable
private fun UniversityCard(u: PlanUniversity, scheme: ColorScheme, onToggleWishlist: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#${u.rank}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = scheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(u.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface, modifier = Modifier.weight(1f))
            Icon(
                if (u.wishlisted) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                contentDescription = if (u.wishlisted) "Remove from wishlist" else "Add to wishlist",
                tint = if (u.wishlisted) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onToggleWishlist).padding(4.dp).size(22.dp),
            )
        }
        Row {
            Text(u.country, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            u.tuition?.let {
                Text(" · $it", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        u.scholarship?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface,
                modifier = Modifier.padding(top = 4.dp))
        }
        u.whyFits?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun MarketCard(m: PlanMarket, scheme: ColorScheme) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(m.country, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        Text("Post-study visa: ${m.postStudyVisa}", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
        Text(m.outlook, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        m.skillsDemand?.let {
            Text("In-demand skills", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = scheme.primary, modifier = Modifier.padding(top = 6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
        }
        m.sourceNote?.let {
            Text("Source: $it", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ScholarshipCard(s: PlanScholarship, scheme: ColorScheme) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(s.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        Text(s.place, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        s.amount?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.primary, modifier = Modifier.padding(top = 4.dp)) }
        s.eligibility?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface) }
        s.deadline?.let { Text("Deadline: $it", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant) }
    }
}
