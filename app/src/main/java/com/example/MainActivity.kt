package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.data.AppScreen
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DocSaathiViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DocSaathiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()
                val userMessage by viewModel.userMessage.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(userMessage) {
                    userMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearUserMessage()
                    }
                }

                BackHandler(enabled = currentScreen != AppScreen.HOME) {
                    viewModel.navigateBack()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Crossfade(
                        targetState = currentScreen,
                        label = "ScreenTransition"
                    ) { screen ->
                        when (screen) {
                            AppScreen.HOME -> HomeScreen(viewModel = viewModel)
                            AppScreen.CAMERA_SCANNER -> CameraScannerScreen(viewModel = viewModel)
                            AppScreen.CROP_EDIT -> CropEditScreen(viewModel = viewModel)
                            AppScreen.PDF_PREVIEW -> PdfPreviewScreen(viewModel = viewModel)
                            AppScreen.FILES -> FilesScreen(viewModel = viewModel)
                            AppScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            AppScreen.EXAM_RESIZER -> ExamResizerScreen(viewModel = viewModel)
                            AppScreen.KB_COMPRESSOR -> KbCompressorScreen(viewModel = viewModel)
                            AppScreen.ID_CARD_SCANNER -> IdCardScannerScreen(viewModel = viewModel)
                        }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    )
                }
            }
        }
    }
}
