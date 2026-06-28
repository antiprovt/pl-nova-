package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE) }

    val employeeNames = remember {
        (RosterData.topEmployees.map { it.name } + RosterData.bottomEmployees.map { it.name }).distinct()
    }

    var selectedName by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }
    var sentCodeDialogValue by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    val isRiegerT = remember(selectedName) {
        val trimmed = selectedName.trim()
        trimmed.equals("RiegerT", ignoreCase = true) || trimmed.equals("Rieger T.", ignoreCase = true)
    }

    val isAdmin = remember(selectedName) {
        selectedName.trim().equals("admin", ignoreCase = true)
    }

    val isTestUser = remember(selectedName) {
        selectedName.trim().equals("test", ignoreCase = true)
    }

    val isNameInRoster = remember(selectedName) {
        val trimmed = selectedName.trim()
        employeeNames.any { it.trim().equals(trimmed, ignoreCase = true) }
    }

    val showEmailOption = isNameInRoster && !isRiegerT && !isAdmin && !isTestUser
    val isSpecialUser = isRiegerT || isAdmin || isTestUser

    fun sendEmailCode() {
        val emailTrimmed = emailInput.trim()
        if (emailTrimmed.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailTrimmed).matches()) {
            errorMessage = "Zadajte platnú e-mailovú adresu."
            return
        }

        errorMessage = null
        val code = (100000..999999).random().toString()
        prefs.edit().putString("roster_login_password", code).apply()
        sentCodeDialogValue = code

        // Real email draft compose creation via Intent to send real-world emails
        try {
            val emailIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:")
                putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(emailTrimmed))
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Prístupový kód pre rozpis zmien")
                putExtra(android.content.Intent.EXTRA_TEXT, "Ahoj,\n\ntvoj jednorazový prístupový kód pre prihlásenie do aplikácie je: $code\n\nZadaj tento kód do poľa pre prihlásenie.")
            }
            context.startActivity(android.content.Intent.createChooser(emailIntent, "Odoslať jednorazový kód..."))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    if (sentCodeDialogValue != null) {
        AlertDialog(
            onDismissRequest = { sentCodeDialogValue = null },
            confirmButton = {
                TextButton(onClick = { sentCodeDialogValue = null }) {
                    Text("Rozumiem")
                }
            },
            title = {
                Text(
                    text = "Prístupový kód odoslaný",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Akčný kód bol odoslaný na zadaný e-mail:")
                    Text(
                        text = emailInput.trim(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Váš prístupový kód pre prihlásenie:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sentCodeDialogValue ?: "",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text(
                        text = "Tento kód skopírujte a zadajte do textového poľa nižšie.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 450.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App Icon Graphic Node
            Surface(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock " + "graphic logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Text Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Chránený prístup",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Na zobrazenie a úpravu rozpisu sa prihláste",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Input fields card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Meno (Selected Employee) Manual Input Text Field only to prevent unauthorized access
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Meno z rozpisu",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = selectedName,
                            onValueChange = { input ->
                                selectedName = input
                                errorMessage = null
                            },
                            placeholder = { Text("Zadajte presné meno") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_username_field"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }

                    // E-mail options dynamically offered only if name is correct and not special
                    if (showEmailOption) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Možnosť e-mail",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = {
                                    emailInput = it
                                    errorMessage = null
                                },
                                placeholder = { Text("napriklad@email.com") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_email_field"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = { sendEmailCode() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("login_send_email_code_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text(
                                    text = "Odoslať kód na e-mail",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Heslo passcode input with toggle visibility
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (isSpecialUser) "Heslo" else "Prístupový kód",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { input ->
                                if (input.length <= 12) {
                                    passwordInput = input
                                    errorMessage = null
                                }
                            },
                            placeholder = { Text(if (isSpecialUser) "Zadajte heslo" else "Zadajte kód") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Skryť heslo" else "Zobraziť heslo"
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_field"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }

                    // Validation alerts
                    errorMessage?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Login Action Primary CTA
                    Button(
                        onClick = {
                            val nameClean = selectedName.trim()
                            if (nameClean.isBlank()) {
                                errorMessage = "Najprv zadajte meno."
                                return@Button
                            }

                            if (nameClean.equals("RiegerT", ignoreCase = true) || nameClean.equals("Rieger T.", ignoreCase = true)) {
                                if (passwordInput == "745325") {
                                    val finalName = employeeNames.find { it.contains("Rieger", ignoreCase = true) } ?: "Rieger T."
                                    prefs.edit()
                                        .putBoolean("is_logged_in", true)
                                        .putString("logged_in_user_name", finalName)
                                        .putString("user_name", finalName)
                                        .putString("user_email", emailInput.trim())
                                        .apply()
                                    onLoginSuccess(finalName)
                                } else {
                                    errorMessage = "Nesprávne heslo pre RiegerT."
                                }
                            } else if (nameClean.equals("admin", ignoreCase = true)) {
                                if (passwordInput == "admin") {
                                    val finalName = "admin"
                                    prefs.edit()
                                        .putBoolean("is_logged_in", true)
                                        .putString("logged_in_user_name", finalName)
                                        .putString("user_name", finalName)
                                        .putString("user_email", emailInput.trim())
                                        .apply()
                                    onLoginSuccess(finalName)
                                } else {
                                    errorMessage = "Nesprávne heslo pre admina."
                                }
                            } else if (nameClean.equals("test", ignoreCase = true)) {
                                if (passwordInput == "test") {
                                    val finalName = "test"
                                    prefs.edit()
                                        .putBoolean("is_logged_in", true)
                                        .putString("logged_in_user_name", finalName)
                                        .putString("user_name", finalName)
                                        .putString("user_email", emailInput.trim())
                                        .apply()
                                    onLoginSuccess(finalName)
                                } else {
                                    errorMessage = "Nesprávne heslo pre test."
                                }
                            } else {
                                val matchedEmployee = employeeNames.find { it.trim().equals(nameClean, ignoreCase = true) }
                                if (matchedEmployee == null) {
                                    errorMessage = "Zadané meno sa v rozpise nenachádza."
                                } else {
                                    val savedPassword = prefs.getString("roster_login_password", "") ?: ""
                                    if (passwordInput == savedPassword && passwordInput.isNotBlank()) {
                                        prefs.edit()
                                            .putBoolean("is_logged_in", true)
                                            .putString("logged_in_user_name", matchedEmployee)
                                            .putString("user_name", matchedEmployee)
                                            .putString("user_email", emailInput.trim())
                                            .apply()
                                        onLoginSuccess(matchedEmployee)
                                    } else {
                                        errorMessage = "Nesprávny prístupový kód pre používateľa $matchedEmployee."
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("login_submit_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = "Prihlásiť sa",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(
                        onClick = { showForgotPasswordDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("login_forgot_password_btn")
                    ) {
                        Text(
                            text = "Zabudol som heslo",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (showForgotPasswordDialog) {
        var forgotName by remember { mutableStateOf("") }
        var forgotEmail by remember { mutableStateOf("") }
        var forgotError by remember { mutableStateOf<String?>(null) }
        var forgotSuccess by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { 
                showForgotPasswordDialog = false
                forgotSuccess = false
                forgotError = null
            },
            confirmButton = {
                if (forgotSuccess) {
                    TextButton(
                        onClick = { 
                            showForgotPasswordDialog = false
                            forgotSuccess = false
                            forgotError = null
                        }
                    ) {
                        Text("Zavrieť")
                    }
                } else {
                    Button(
                        onClick = {
                            val nameClean = forgotName.trim()
                            val emailClean = forgotEmail.trim()
                            if (nameClean.isBlank()) {
                                forgotError = "Najprv zadajte meno."
                                return@Button
                            }
                            if (emailClean.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailClean).matches()) {
                                forgotError = "Zadajte platnú e-mailovú adresu."
                                return@Button
                            }

                            // Check if name is valid
                            val isFound = nameClean.lowercase() == "admin" || 
                                          nameClean.lowercase() == "riegert" || 
                                          nameClean.lowercase() == "rieger t." ||
                                          employeeNames.any { it.trim().equals(nameClean, ignoreCase = true) }

                            if (!isFound) {
                                forgotError = "Zadané meno sa v rozpise nenachádza."
                                return@Button
                            }

                            forgotError = null
                            // Generate new numeric password for non-special users, or retrieve theirs
                            val newPass = if (nameClean.lowercase() == "admin") {
                                "admin"
                            } else if (nameClean.lowercase() == "riegert" || nameClean.lowercase() == "rieger t.") {
                                "745325"
                            } else {
                                (100000..999999).random().toString()
                            }
                            
                            // If it's a regular user, save this as the new password in SharedPreferences
                            if (nameClean.lowercase() != "admin" && nameClean.lowercase() != "riegert" && nameClean.lowercase() != "rieger t.") {
                                prefs.edit().putString("roster_login_password", newPass).apply()
                            }
                            
                            // Save user email to shared preferences
                            prefs.edit().putString("user_email", emailClean).apply()

                            // Send via email client intent
                            try {
                                val emailIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                    data = android.net.Uri.parse("mailto:")
                                    putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(emailClean))
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Nové heslo pre rozpis zmien")
                                    putExtra(
                                        android.content.Intent.EXTRA_TEXT, 
                                        "Ahoj,\n\ntvoje nové heslo pre prihlásenie pod menom $nameClean je: $newPass\n\nTýmto heslom sa teraz môžeš prihlásiť do aplikácie."
                                    )
                                }
                                context.startActivity(android.content.Intent.createChooser(emailIntent, "Odoslať nové heslo..."))
                                forgotSuccess = true
                            } catch (e: Exception) {
                                forgotError = "Nepodarilo sa otvoriť e-mailového klienta: ${e.localizedMessage}"
                            }
                        }
                    ) {
                        Text("Odoslať nové heslo")
                    }
                }
            },
            dismissButton = {
                if (!forgotSuccess) {
                    TextButton(onClick = { showForgotPasswordDialog = false }) {
                        Text("Zrušiť")
                    }
                }
            },
            title = {
                Text(
                    text = "Zabudol som heslo",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (forgotSuccess) {
                        Text(
                            text = "Nové heslo bolo vygenerované a pripravené na odoslanie na e-mail: $forgotEmail",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Po otvorení e-mailového klienta odošlite správu a prihláste sa novým heslom.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Zadajte svoje meno z rozpisu a prihlasovací e-mail. Na zadaný e-mail vám zašleme nové heslo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = forgotName,
                            onValueChange = { 
                                forgotName = it
                                forgotError = null
                            },
                            label = { Text("Meno z rozpisu") },
                            placeholder = { Text("Napr. admin, Rieger T. alebo Jozef") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("forgot_password_name_field"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = forgotEmail,
                            onValueChange = { 
                                forgotEmail = it
                                forgotError = null
                            },
                            label = { Text("E-mailová adresa") },
                            placeholder = { Text("vas@email.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth().testTag("forgot_password_email_field"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        forgotError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        )
    }
}
