package id.plegogel3.ruangkendali.parent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class MainActivity : ComponentActivity() {
 private fun setupFirebase() {
  if(FirebaseApp.getApps(this).isNotEmpty()||BuildConfig.FIREBASE_API_KEY.startsWith("CONFIGURE"))return
  FirebaseApp.initializeApp(this,FirebaseOptions.Builder().setApiKey(BuildConfig.FIREBASE_API_KEY).setApplicationId(BuildConfig.FIREBASE_APP_ID).setProjectId(BuildConfig.FIREBASE_PROJECT_ID).build())
 }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setupFirebase()
  setContent { MaterialTheme {
   var code by remember{mutableStateOf("")};var status by remember{mutableStateOf("Masuk akun Firebase lalu masukkan kode dari perangkat Child.")};var busy by remember{mutableStateOf(false)}
   val scope=rememberCoroutineScope()
   Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
    Text("Ruang Kendali Bersama",style=MaterialTheme.typography.headlineMedium)
    Text("Parent • Hubungkan perangkat",style=MaterialTheme.typography.titleMedium)
    if(FirebaseApp.getApps(this@MainActivity).isNotEmpty()) Button(onClick={FirebaseAuth.getInstance().signInAnonymously().addOnSuccessListener{status="Sesi Firebase aktif. Masukkan kode Child."}.addOnFailureListener{status="Login gagal: ${it.message}"}}){Text("Mulai sesi Firebase (uji)")}
    if(FirebaseApp.getApps(this@MainActivity).isEmpty())Text("Firebase belum dikonfigurasi. Atur API key, App ID, Project ID dan aktifkan Anonymous Authentication.")
    OutlinedTextField(value=code,onValueChange={code=it.filter(Char::isDigit).take(6)},label={Text("Kode pairing 6 digit")},singleLine=true)
    Button(enabled=code.length==6&&!busy&&FirebaseApp.getApps(this@MainActivity).isNotEmpty(),onClick={scope.launch{
     busy=true
     try{val user=FirebaseAuth.getInstance().currentUser
      if(user==null){status="Belum login. Autentikasi Firebase perlu disiapkan.";return@launch}
      val token=user.getIdToken(false).awaitToken()
      val result=withContext(Dispatchers.IO){val c=URL(BuildConfig.WORKER_BASE_URL.trimEnd('/')+"/pair/redeem").openConnection() as HttpURLConnection
       try{c.requestMethod="POST";c.connectTimeout=12000;c.readTimeout=12000;c.doOutput=true;c.setRequestProperty("Authorization","Bearer $token");c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(JSONObject().put("code",code).toString().toByteArray())};val s=if(c.responseCode in 200..299)c.inputStream else c.errorStream;val txt=s.bufferedReader().use{it.readText()};if(c.responseCode !in 200..299)throw IllegalStateException(JSONObject(txt).optString("error","HTTP ${c.responseCode}"));JSONObject(txt)}finally{c.disconnect()}}
      status="Pairing berhasil untuk perangkat ${result.optString("deviceId")}."
      code=""
     }catch(e:Exception){status="Pairing gagal: ${e.message ?: "periksa koneksi dan Worker"}"}finally{busy=false}
    }}){Text(if(busy)"Memproses…" else "Hubungkan perangkat")}
    Text(status)
    Text("Kode kedaluwarsa dalam 5 menit dan hanya dapat dipakai sekali. Pastikan perangkat Child dan identitas keluarga benar.",style=MaterialTheme.typography.bodySmall)
   }
  } }
 }
}
private suspend fun com.google.android.gms.tasks.Task<com.google.firebase.auth.GetTokenResult>.awaitToken():String =
 kotlinx.coroutines.suspendCancellableCoroutine { c -> addOnSuccessListener { c.resume(it.token.orEmpty(),null) }.addOnFailureListener{c.resumeWith(Result.failure(it))} }
