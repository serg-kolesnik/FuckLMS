package com.fucklms.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var accountManager: AccountManager

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accountManager = AccountManager(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MaterialTheme {
                MainAppScreen(accountManager)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(accountManager: AccountManager) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var scannedUrl by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    when (selectedTab) {
                        0 -> "Сканер LMS"
                        1 -> "Встроенный браузер"
                        else -> "Мои аккаунты"
                    }
                )
            })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("📷") },
                    label = { Text("Сканер") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("🌐") },
                    label = { Text("Браузер") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("👤") },
                    label = { Text("Аккаунты") }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> ScannerScreen(
                    accountManager = accountManager,
                    onQrScanned = { url ->
                        scannedUrl = url
                        selectedTab = 1
                    }
                )
                1 -> {
                    // ИЗМЕНЕНИЕ: Передаем в браузер только ВЫБРАННЫЕ аккаунты
                    val selectedAccounts = accountManager.getAccounts().filter { it.isSelected }
                    BrowserScreen(
                        url = scannedUrl,
                        accounts = selectedAccounts.map { Pair(it.login, it.password) } // Конвертируем для браузера
                    )
                }
                2 -> AccountsScreen(accountManager)
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun ScannerScreen(accountManager: AccountManager, onQrScanned: (String) -> Unit) {
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val scanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                    )

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        barcode.rawValue?.let { url -> onQrScanned(url) }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            context as ComponentActivity,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (exc: Exception) {
                        Log.e("MoodleApp", "Camera bind failed", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ================== БРАУЗЕР ==================
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(url: String, accounts: List<Pair<String, String>>) {
    if (url.isEmpty() || accounts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (url.isEmpty()) "Отсканируйте QR-код" else "Нет выбранных аккаунтов!",
                style = MaterialTheme.typography.bodyLarge,
                color = if (url.isNotEmpty()) Color.Red else Color.Unspecified
            )
        }
        return
    }

    var currentAccountIndex by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("Инициализация...") }

    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
    }

    val account = accounts.getOrNull(currentAccountIndex)

    if (account == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Все выбранные аккаунты отмечены!", style = MaterialTheme.typography.headlineMedium, color = Color.Green)
        }
        return
    }

    LaunchedEffect(currentAccountIndex) {
        if (account != null) {
            statusText = "Вход как ${account.first}..."

            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, currentUrl: String?) {
                    super.onPageFinished(view, currentUrl)
                    if (view == null || currentUrl == null) return

                    if (currentUrl.contains("login/index.php")) {
                        val js = """
                            javascript:(function() {
                                var user = document.getElementById('username');
                                var pass = document.getElementById('password');
                                if (user && pass) {
                                    user.value = '${account.first}';
                                    pass.value = '${account.second}';
                                    document.getElementById('loginbtn').click();
                                }
                            })()
                        """.trimIndent()
                        view.evaluateJavascript(js, null)
                    }
                    else if (currentUrl.contains("attendance.php")) {
                        statusText = "Анализ страницы..."
                        val qrPass = parseQrPassword(url)

                        val js = """
                            javascript:(function() {
                                var bodyText = document.body.innerText;
                                
                                if (bodyText.includes('уже была отмечена') || bodyText.includes('already been marked')) {
                                    window.location.href = 'https://lms.uni-dubna.ru/mod/attendance/view.php?id=122864&success=true';
                                    return;
                                }

                                var radios = document.querySelectorAll('input[type="radio"][name="status"]');
                                var clicked = false;
                                var presentWords = ['present', 'присутств', 'был', 'была', 'были', 'явился', 'явилась', 'уважительн'];
                                
                                for (var i = 0; i < radios.length; i++) {
                                    var parentText = radios[i].parentElement.innerText.toLowerCase();
                                    var fullText = (radios[i].nextSibling ? radios[i].nextSibling.textContent : "") + parentText;
                                    
                                    for (var j = 0; j < presentWords.length; j++) {
                                        if (fullText.includes(presentWords[j])) {
                                            radios[i].checked = true;
                                            clicked = true;
                                            break;
                                        }
                                    }
                                    if (clicked) break;
                                }
                                
                                if (!clicked && radios.length > 0) {
                                    radios[0].checked = true;
                                }

                                var qrInput = document.querySelector('input[name="studentpassword"]');
                                if (qrInput && '$qrPass' !== "") {
                                    qrInput.value = '$qrPass';
                                }
                                
                                var saveBtn = document.querySelector('button[type="submit"], input[type="submit"]');
                                if (saveBtn) saveBtn.click();
                            })()
                        """.trimIndent()
                        view.evaluateJavascript(js, null)
                    }
                    else if (currentUrl.contains("success=true") || currentUrl.contains("attendance/view.php")) {
                        statusText = "Успех! Переход..."
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            currentAccountIndex++
                        }, 1500)
                    }
                    else if (!currentUrl.contains("attendance")) {
                        statusText = "Редирект..."
                        view.loadUrl(url)
                    }
                }
            }
            webView.loadUrl(url)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Аккаунт ${currentAccountIndex + 1} из ${accounts.size}: ${account.first}\nСтатус: $statusText",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.primary
        )

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { webView }
        )
    }
}

fun parseQrPassword(url: String): String {
    return try {
        val uri = android.net.Uri.parse(url)
        uri.getQueryParameter("qrpass") ?: ""
    } catch (e: Exception) {
        ""
    }
}

// ================== АККАУНТЫ ==================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(accountManager: AccountManager) {
    // ИЗМЕНЕНИЕ: Теперь используем UserAccount вместо Pair
    var accounts by remember { mutableStateOf(accountManager.getAccounts()) }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Добавить аккаунт", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = login, onValueChange = { login = it },
            label = { Text("Логин") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (login.isNotBlank() && password.isNotBlank()) {
                    accountManager.addAccount(login, password)
                    accounts = accountManager.getAccounts()
                    login = ""
                    password = ""
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) { Text("Добавить") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text("Список аккаунтов (${accounts.size})", style = MaterialTheme.typography.titleMedium)

        LazyColumn {
            items(accounts) { account ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // ИЗМЕНЕНИЕ: Добавили CheckBox
                            Checkbox(
                                checked = account.isSelected,
                                onCheckedChange = {
                                    accountManager.toggleAccountSelection(account.login)
                                    accounts = accountManager.getAccounts() // Обновляем список
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = account.login, style = MaterialTheme.typography.bodyLarge)
                        }

                        IconButton(onClick = {
                            accountManager.removeAccount(account.login)
                            accounts = accountManager.getAccounts()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}