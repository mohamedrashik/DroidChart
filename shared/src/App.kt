package com.droid.bootchart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.droid.bootchart.data.BootchartRepositoryImpl
import com.droid.bootchart.data.createBootchartSource
import com.droid.bootchart.domain.ImportBootchart
import com.droid.bootchart.presentation.DroidChartTheme
import com.droid.bootchart.presentation.HomeScreen
import com.droid.bootchart.presentation.HomeStateHolder
import kotlinx.coroutines.launch
import org.jetbrains.compose.reload.DevelopmentEntryPoint

@Composable
@Preview
@DevelopmentEntryPoint
fun App() {
    val stateHolder = remember {
        HomeStateHolder(ImportBootchart(BootchartRepositoryImpl(createBootchartSource())))
    }
    val scope = rememberCoroutineScope()
    var dark by remember { mutableStateOf(true) }
    DroidChartTheme(dark) {
        HomeScreen(stateHolder.state, dark, { dark = !dark }, { scope.launch { stateHolder.import() } })
    }
}
