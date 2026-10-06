package com.kirolos.todoapp

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

private fun authError(e: Exception?): String = when (e) {
    is FirebaseAuthWeakPasswordException -> "الباسورد ضعيف، استخدم ٦ حروف أو أرقام على الأقل"
    is FirebaseAuthUserCollisionException -> "الإيميل ده متسجل قبل كده، جرّب تسجّل دخول"
    is FirebaseAuthInvalidUserException -> "مفيش حساب بالإيميل ده"
    is FirebaseAuthInvalidCredentialsException -> "الإيميل أو الباسورد غلط"
    is FirebaseNetworkException -> "مفيش اتصال بالإنترنت"
    else -> "حصلت مشكلة، حاول تاني"
}

/** بيطلع قايمة حسابات جوجل اللي على الموبايل ويرجّع ID token نبدّله بجلسة Firebase */
private suspend fun googleIdToken(context: Context): String {
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(context.getString(R.string.default_web_client_id))
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = CredentialManager.create(context).getCredential(context, request).credential
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    throw IllegalStateException("unexpected credential type")
}

/** شاشة تسجيل الدخول / إنشاء حساب بنفس تصميم الزجاج */
@Composable
fun AuthScreen() {
    val g = LocalGlass.current
    val auth = remember { FirebaseAuth.getInstance() }
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val canSubmit = !busy && email.isNotBlank() && pass.isNotEmpty()

    fun google() {
        if (busy) return
        busy = true; error = null; info = null
        scope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(googleIdToken(context), null)
                auth.signInWithCredential(credential).addOnCompleteListener { t ->
                    busy = false
                    if (!t.isSuccessful) error = authError(t.exception)
                    // لو نجح، حالة الدخول بتتغير لوحدها والشاشة الرئيسية بتظهر
                }
            } catch (e: GetCredentialCancellationException) {
                busy = false   // قفل القايمة: مفيش رسالة
            } catch (e: NoCredentialException) {
                busy = false
                error = "مفيش حساب جوجل على الموبايل، ضيف حساب من إعدادات الموبايل وجرّب تاني"
            } catch (e: GetCredentialException) {
                busy = false
                error = "تسجيل الدخول بجوجل ماكملش، اتأكد من إضافة SHA-1 في Firebase (راجع الـ README)"
            } catch (e: Exception) {
                busy = false
                error = "حصلت مشكلة، حاول تاني"
            }
        }
    }

    fun submit() {
        if (!canSubmit) return
        busy = true; error = null; info = null
        val e = email.trim()
        val task = if (register) auth.createUserWithEmailAndPassword(e, pass)
        else auth.signInWithEmailAndPassword(e, pass)
        task.addOnCompleteListener { t ->
            busy = false
            if (!t.isSuccessful) error = authError(t.exception)
            // لو نجح، حالة الدخول بتتغير لوحدها والشاشة الرئيسية بتظهر
        }
    }

    fun reset() {
        if (email.isBlank()) { error = "اكتب الإيميل الأول"; return }
        busy = true; error = null; info = null
        auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener { t ->
            busy = false
            if (t.isSuccessful) info = "بعتنالك لينك لتغيير الباسورد على الإيميل"
            else error = authError(t.exception)
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(Brush.linearGradient(listOf(g.blueLight, g.blue))),
            contentAlignment = Alignment.Center
        ) { Text("✓", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(18.dp))
        Text("المهام", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = g.label)
        Text(
            "سجّل دخولك عشان مهامك تتزامن بين الموبايل وإضافة كروم",
            fontSize = 15.sp, color = g.secondary, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp)
        )

        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), strong = true).padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable(enabled = !busy, onClick = ::google)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(10.dp))
                Text(
                    "المتابعة باستخدام Google",
                    color = Color(0xFF1F1F1F), fontSize = 16.sp, fontWeight = FontWeight.Medium
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f).height(1.dp).background(g.separator))
                Text("أو بالإيميل", color = g.secondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
                Box(Modifier.weight(1f).height(1.dp).background(g.separator))
            }
            Segmented(listOf("تسجيل دخول", "حساب جديد"), if (register) 1 else 0) {
                register = it == 1; error = null; info = null
            }
            Spacer(Modifier.height(16.dp))
            AuthField("الإيميل", email, { email = it }, false, KeyboardType.Email)
            Spacer(Modifier.height(10.dp))
            AuthField("الباسورد", pass, { pass = it }, true, KeyboardType.Password)

            error?.let {
                Text(it, color = g.red, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            }
            info?.let {
                Text(it, color = g.green, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            }

            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                    .background(
                        if (canSubmit) Brush.linearGradient(listOf(g.blueLight, g.blue))
                        else SolidColor(g.control)
                    )
                    .clickable(enabled = canSubmit, onClick = ::submit)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (busy) "ثواني…" else if (register) "إنشاء حساب" else "دخول",
                    color = if (canSubmit) Color.White else g.secondary,
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                )
            }
            if (!register) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = ::reset, enabled = !busy) {
                        Text("نسيت الباسورد؟", color = g.blue, fontSize = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AuthField(
    label: String, value: String, onChange: (String) -> Unit, password: Boolean, type: KeyboardType
) {
    val g = LocalGlass.current
    Column {
        Text(label, fontSize = 13.sp, color = g.secondary, modifier = Modifier.padding(start = 6.dp, bottom = 5.dp))
        Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(
                    color = g.label, fontSize = 17.sp,
                    textDirection = TextDirection.Ltr, textAlign = TextAlign.Left
                ),
                cursorBrush = SolidColor(g.blue),
                keyboardOptions = KeyboardOptions(keyboardType = type),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
