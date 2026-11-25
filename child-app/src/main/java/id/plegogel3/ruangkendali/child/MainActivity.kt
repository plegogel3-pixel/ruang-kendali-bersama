package id.plegogel3.ruangkendali.child

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import android.provider.Settings
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
 private fun setupFirebase() {
  if (FirebaseApp.getApps(this).isNotEmpty()) return
  if (BuildConfig.FIREBASE_API_KEY.startsWith("CONFIGURE")) return
  val options=FirebaseOptions.Builder().setApiKey(BuildConfig.FIREBASE_API_KEY)
   .setApplicationId(BuildConfig.FIREBASE_APP_ID).setProjectId(BuildConfig.FIREBASE_PROJECT_ID).build()
  FirebaseApp.initializeApp(this,options)
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState); setupFirebase()
  val prefs=getSharedPreferences("device",MODE_PRIVATE)
  val deviceId=prefs.getString("id",null) ?: java.util.UUID.randomUUID().toString().replace("-","").also{prefs.edit().putString("id",it).apply()}
  setContent { MaterialTheme {
   var code by remember { mutableStateOf("") }; var message by remember { mutableStateOf("Masuk akun Firebase, lalu buat kode pairing.") }; var busy by remember { mutableStateOf(false) }; var locationBusy by remember { mutableStateOf(false) }
   val scope= rememberCoroutineScope()
   Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
    Text("Ruang Kendali Bersama",style=MaterialTheme.typography.headlineMedium)
    Text("Child • Pairing perangkat",style=MaterialTheme.typography.titleMedium)
    Text("Kode hanya tampil di perangkat ini, berlaku 5 menit, dan hanya dapat digunakan sekali.")
    if(FirebaseApp.getApps(this@MainActivity).isNotEmpty()) Button(onClick={FirebaseAuth.getInstance().signInAnonymously().addOnSuccessListener{message="Sesi Firebase aktif. Kode dapat dibuat."}.addOnFailureListener{message="Login gagal: ${it.message}"}}){Text("Mulai sesi Firebase (uji)")}
    if(FirebaseApp.getApps(this@MainActivity).isEmpty()) {
     Text("Firebase belum dikonfigurasi. Isi Firebase API key, App ID, Project ID dan aktifkan Anonymous Authentication di Firebase Console.")
    } else {
     Button(enabled=!busy,onClick={scope.launch {
      busy=true
      try {
       val user=FirebaseAuth.getInstance().currentUser
       if(user==null){message="Belum login. Gunakan Firebase Authentication terlebih dahulu.";return@launch}
       val token=user.getIdToken(false).awaitToken()
       val result=withContext(Dispatchers.IO){postJson("/pair/create",JSONObject().put("deviceId",deviceId),token)}
       code=result.optString("code");message="Kode dibuat. Jangan bagikan selain kepada orang tua/wali yang kamu setujui."
      }catch(e:Exception){message="Gagal membuat kode: ${e.message ?: "periksa koneksi dan Worker"}"}finally{busy=false}
     }}) { Text(if(busy)"Memproses…" else "Buat kode 6 digit") }
    }
    Button(enabled=!locationBusy,onClick={
     val fine=ActivityCompat.checkSelfPermission(this@MainActivity,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
     val coarse=ActivityCompat.checkSelfPermission(this@MainActivity,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
     if(!fine&&!coarse){ActivityCompat.requestPermissions(this@MainActivity,arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),42);message="Izinkan lokasi saat diminta, lalu tekan tombol kirim lokasi lagi."}
     else scope.launch {
      locationBusy=true
      try {
       val lm=getSystemService(LOCATION_SERVICE) as LocationManager
       val provider=if(fine)LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
       val loc=lm.getLastKnownLocation(provider) ?: throw IllegalStateException("Lokasi belum tersedia. Aktifkan lokasi/GPS, buka Maps sebentar, lalu coba lagi.")
       val user=FirebaseAuth.getInstance().currentUser ?: throw IllegalStateException("Sesi Firebase belum aktif.")
       val token=user.getIdToken(false).awaitToken()
       val body=JSONObject().put("deviceId",deviceId).put("latitude",loc.latitude).put("longitude",loc.longitude).put("accuracy",loc.accuracy.toDouble())
       val result=withContext(Dispatchers.IO){postJson("/location/update",body,token)}
       message="Lokasi berhasil dibagikan pada ${java.text.DateFormat.getTimeInstance().format(java.util.Date(result.optLong("updatedAt")*1000))}. Tekan lagi untuk memperbarui."
      }catch(e:Exception){message="Gagal mengirim lokasi: ${e.message ?: "periksa izin dan koneksi"}"}finally{locationBusy=false}
     }
    }) { Text(if(locationBusy)"Mengirim lokasi…" else "Bagikan lokasi sekarang") }
    Text("Lokasi hanya dikirim setelah izin Android diberikan dan tombol ini ditekan. Pembaruan dilakukan manual saat aplikasi dibuka.",style=MaterialTheme.typography.bodySmall)
    if(code.isNotBlank()) Text(code,style=MaterialTheme.typography.displayLarge)
    Text(message)
    Text("ID perangkat lokal: ${deviceId.take(8)}…",style=MaterialTheme.typography.bodySmall)
   }
  } }
 }
 private suspend fun postJson(path:String,body:JSONObject,token:String)=withContext(Dispatchers.IO){
  val conn=(URL(BuildConfig.WORKER_BASE_URL.trimEnd('/')+path).openConnection() as HttpURLConnection)
  try {conn.requestMethod="POST";conn.connectTimeout=12000;conn.readTimeout=12000;conn.doOutput=true
   conn.setRequestProperty("Authorization","Bearer $token");conn.setRequestProperty("Content-Type","application/json")
   conn.outputStream.use{it.write(body.toString().toByteArray())}
   val stream=if(conn.responseCode in 200..299)conn.inputStream else conn.errorStream
   val response=stream.bufferedReader().use{it.readText()}
   if(conn.responseCode !in 200..299) throw IllegalStateException(JSONObject(response).optString("error","HTTP ${conn.responseCode}"))
   JSONObject(response)
  }finally{conn.disconnect()}
 }
}
private suspend fun com.google.android.gms.tasks.Task<com.google.firebase.auth.GetTokenResult>.awaitToken():String =
 kotlinx.coroutines.suspendCancellableCoroutine { c -> addOnSuccessListener { c.resume(it.token.orEmpty(),null) }.addOnFailureListener{c.resumeWith(Result.failure(it))} }
