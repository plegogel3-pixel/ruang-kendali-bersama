package id.plegogel3.ruangkendali.child

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent { MaterialTheme { Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text("Ruang Kendali Bersama", style=MaterialTheme.typography.headlineMedium)
   Text("Child • Aktivasi transparan")
   Text("Pairing dan izin perangkat belum diaktifkan. Aplikasi belum mengumpulkan lokasi atau data.")
   Text("Aktivasi layanan memerlukan konfigurasi backend dan persetujuan pengguna.")
  } } }
 }
}
