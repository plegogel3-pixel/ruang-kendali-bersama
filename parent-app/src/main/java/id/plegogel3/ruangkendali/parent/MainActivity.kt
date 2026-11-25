package id.plegogel3.ruangkendali.parent

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
   val auth=remember { if(FirebaseApp.getApps(this@MainActivity).isNotEmpty()) FirebaseAuth.getInstance() else null }
   var signedIn by remember { mutableStateOf(auth?.currentUser!=null) }
   var register by remember { mutableStateOf(false) }
   var email by remember{mutableStateOf("")};var password by remember{mutableStateOf("")}
   var code by remember{mutableStateOf("")};var status by remember{mutableStateOf("Buat akun atau login menggunakan email dan sandi.")};var busy by remember{mutableStateOf(false)};var locationBusy by remember{mutableStateOf(false)};var linkedDeviceId by remember{mutableStateOf(getSharedPreferences("parent",MODE_PRIVATE).getString("linked_device_id","") ?: "")};var lastLat by remember{mutableStateOf<Double?>(null)};var lastLon by remember{mutableStateOf<Double?>(null)}
   val scope=rememberCoroutineScope()
   Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
    Text("Ruang Kendali Bersama",style=MaterialTheme.typography.headlineMedium)
    Text("Parent • Akun dan hubungkan perangkat",style=MaterialTheme.typography.titleMedium)
    if(auth==null) Text("Firebase belum dikonfigurasi. Periksa Firebase API key, App ID, Project ID.")
    else if(!signedIn) {
     OutlinedTextField(value=email,onValueChange={email=it.trim()},label={Text("Email")},singleLine=true)
     OutlinedTextField(value=password,onValueChange={password=it},label={Text("Sandi (minimal 6 karakter)")},singleLine=true,visualTransformation=PasswordVisualTransformation())
     Button(enabled=email.contains("@")&&password.length>=6&&!busy,onClick={
      busy=true;status="Memproses akun…"
      val task=if(register)auth.createUserWithEmailAndPassword(email,password) else auth.signInWithEmailAndPassword(email,password)
      task.addOnSuccessListener{signedIn=true;status="Login berhasil. Sekarang masukkan kode Child.";password=""}
       .addOnFailureListener{status=if(register)"Pendaftaran gagal: ${it.localizedMessage}" else "Login gagal: ${it.localizedMessage}"}
       .addOnCompleteListener{busy=false}
     }){Text(if(busy)"Memproses…" else if(register)"Daftar akun" else "Login")}
     TextButton(onClick={register=!register;status=if(register)"Masukkan email dan sandi untuk membuat akun." else "Masuk dengan akun yang sudah dibuat."}){Text(if(register)"Sudah punya akun? Login" else "Belum punya akun? Daftar")}
    } else {
     Text("Login sebagai: ${auth.currentUser?.email ?: "akun Firebase"}")
     TextButton(onClick={auth.signOut();signedIn=false;status="Anda sudah logout."}){Text("Logout")}
     OutlinedTextField(value=code,onValueChange={code=it.filter(Char::isDigit).take(6)},label={Text("Kode pairing 6 digit")},singleLine=true)
     Button(enabled=code.length==6&&!busy,onClick={scope.launch{
      busy=true
      try{val user=auth.currentUser ?: throw IllegalStateException("Silakan login kembali")
       val token=user.getIdToken(false).awaitToken()
       val result=withContext(Dispatchers.IO){val c=URL(BuildConfig.WORKER_BASE_URL.trimEnd('/')+"/pair/redeem").openConnection() as HttpURLConnection
        try{c.requestMethod="POST";c.connectTimeout=12000;c.readTimeout=12000;c.doOutput=true;c.setRequestProperty("Authorization","Bearer $token");c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(JSONObject().put("code",code).toString().toByteArray())};val s=if(c.responseCode in 200..299)c.inputStream else c.errorStream;val txt=s.bufferedReader().use{it.readText()};if(c.responseCode !in 200..299)throw IllegalStateException(JSONObject(txt).optString("error","HTTP ${c.responseCode}"));JSONObject(txt)}finally{c.disconnect()}}
       linkedDeviceId=result.optString("deviceId");getSharedPreferences("parent",MODE_PRIVATE).edit().putString("linked_device_id",linkedDeviceId).apply();status="Pairing berhasil. ID perangkat tersimpan.";code=""
      }catch(e:Exception){status="Pairing gagal: ${e.message ?: "periksa koneksi dan Worker"}"}finally{busy=false}
     }}){Text(if(busy)"Memproses…" else "Hubungkan perangkat")}
    }
     if(linkedDeviceId.isNotBlank()){
      Text("Perangkat terpasang: ${linkedDeviceId.take(8)}…")
      Button(enabled=!locationBusy,onClick={scope.launch{
       locationBusy=true
       try{
        val user=auth.currentUser ?: throw IllegalStateException("Silakan login kembali")
        val token=user.getIdToken(false).awaitToken()
        val data=withContext(Dispatchers.IO){
         val c=URL(BuildConfig.WORKER_BASE_URL.trimEnd('/')+"/location/latest").openConnection() as HttpURLConnection
         try{c.requestMethod="POST";c.connectTimeout=12000;c.readTimeout=12000;c.doOutput=true;c.setRequestProperty("Authorization","Bearer $token");c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(JSONObject().put("deviceId",linkedDeviceId).toString().toByteArray())};val s=if(c.responseCode in 200..299)c.inputStream else c.errorStream;val txt=s.bufferedReader().use{it.readText()};if(c.responseCode !in 200..299)throw IllegalStateException(JSONObject(txt).optString("error","HTTP ${c.responseCode}"));JSONObject(txt)}finally{c.disconnect()}
        }
        lastLat=data.getDouble("latitude");lastLon=data.getDouble("longitude")
        val whenText=java.text.DateFormat.getDateTimeInstance().format(java.util.Date(data.getLong("updated_at")*1000))
        status="Lokasi terakhir: ${lastLat}, ${lastLon} • ${whenText}"
       }catch(e:Exception){status="Belum dapat mengambil lokasi: ${e.message ?: "periksa koneksi"}"}finally{locationBusy=false}
      }}){Text(if(locationBusy)"Memuat lokasi…" else "Lihat lokasi terakhir")}
      if(lastLat!=null&&lastLon!=null)Button(onClick={startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:${lastLat},${lastLon}?q=${lastLat},${lastLon}(Child)")))}){Text("Buka lokasi di Maps")}
     }
    Text(status)
    Text("Gunakan akun sendiri. Jangan bagikan sandi atau kode pairing kepada orang yang tidak dipercaya.",style=MaterialTheme.typography.bodySmall)
   }
  } }
 }
}
private suspend fun com.google.android.gms.tasks.Task<com.google.firebase.auth.GetTokenResult>.awaitToken():String =
 kotlinx.coroutines.suspendCancellableCoroutine { c -> addOnSuccessListener { c.resume(it.token.orEmpty(),null) }.addOnFailureListener{c.resumeWith(Result.failure(it))} }
