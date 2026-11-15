package com.freshkeep.app.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.freshkeep.app.ui.components.GroteKnop
import com.freshkeep.app.ui.components.LeegScherm
import com.freshkeep.app.util.Fotos
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Toont [content] als de camera-toestemming er is. Anders: uitleg + knop om toestemming te geven,
 * en bij definitieve weigering een knop naar de systeeminstellingen.
 */
@Composable
fun CameraToestemming(
    alternatief: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var heeftToestemming by remember { mutableStateOf(heeftCameraToestemming(context)) }
    var gevraagd by rememberSaveable { mutableStateOf(false) }
    var definitiefGeweigerd by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        heeftToestemming = ok
        gevraagd = true
        val activity = context.vindActivity()
        definitiefGeweigerd = !ok && activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
    }

    // Opnieuw controleren als de gebruiker terugkomt uit de instellingen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) heeftToestemming = heeftCameraToestemming(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!heeftToestemming && !gevraagd) launcher.launch(Manifest.permission.CAMERA)
    }

    if (heeftToestemming) {
        content()
    } else if (gevraagd) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LeegScherm(
                icoon = Icons.Outlined.NoPhotography,
                titel = "Camera nodig",
                uitleg = if (definitiefGeweigerd) {
                    "FreshKeep heeft de camera nodig om barcodes en datums te lezen. Zet de toestemming aan bij Instellingen > Machtigingen > Camera."
                } else {
                    "FreshKeep gebruikt de camera alleen om barcodes te scannen en de vervaldatum te fotograferen. Foto's worden niet bewaard op een server."
                },
            )
            GroteKnop(
                tekst = if (definitiefGeweigerd) "Open instellingen" else "Toestemming geven",
                onClick = {
                    if (definitiefGeweigerd) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } else {
                        launcher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (alternatief != null) {
                Spacer(Modifier.height(8.dp))
                alternatief()
            }
        }
    }
}

private fun heeftCameraToestemming(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.vindActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.vindActivity()
    else -> null
}

/** Cameravoorbeeld dat continu EAN-13, EAN-8, UPC-A en UPC-E zoekt (volledig offline, ML Kit). */
@Composable
fun BarcodeCamera(
    zaklamp: Boolean,
    onBarcode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onBarcode)
    val scanner: BarcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build(),
        )
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }

    DisposableEffect(lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { resultaat ->
                resultaat.getValue(scanner)
                    ?.firstOrNull { !it.rawValue.isNullOrBlank() }
                    ?.rawValue
                    ?.let(callback)
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            scanner.close()
        }
    }

    LaunchedEffect(zaklamp) { controller.enableTorch(zaklamp) }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
        modifier = modifier,
    )
}

/** Cameravoorbeeld om een foto te nemen. Gebruik [rememberFotoController] + [neemFoto]. */
@Composable
fun FotoCamera(controller: LifecycleCameraController, zaklamp: Boolean, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    LaunchedEffect(zaklamp) { controller.enableTorch(zaklamp) }
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
        modifier = modifier,
    )
}

@Composable
fun rememberFotoController(): LifecycleCameraController {
    val context = LocalContext.current
    return remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }
}

/** Neemt een foto en geeft een verkleinde JPEG (max 1024 px) terug. */
suspend fun LifecycleCameraController.neemFoto(context: Context): ByteArray {
    val proxy: ImageProxy = suspendCancellableCoroutine { cont ->
        takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    if (cont.isActive) cont.resume(image) else image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    if (cont.isActive) cont.resumeWithException(exception)
                }
            },
        )
    }
    return withContext(Dispatchers.Default) {
        proxy.use { Fotos.naarJpeg(it.toBitmap(), it.imageInfo.rotationDegrees) }
    }
}

/** Kleine "of"-knop onder camera-uitleg. */
@Composable
fun AlternatiefKnop(tekst: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(tekst, style = MaterialTheme.typography.titleSmall) }
}
