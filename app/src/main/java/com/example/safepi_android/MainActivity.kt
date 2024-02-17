package com.example.safepi_android

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.safepi_android.ui.theme.SafePi_AndroidTheme
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.Column
import android.util.Log
import android.os.Build
import java.util.UUID
import android.bluetooth.BluetoothGattCharacteristic
import android.widget.Toast
import java.util.Arrays
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class MainActivity : ComponentActivity() {
    private val bluetoothManager: BluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    private val bluetoothLeScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())

    // Stops scanning after 10 seconds.
    private val SCANPERIOD: Long = 30000
    private val devices = mutableStateListOf<ScanResult>()
    private var connectionStatusMessage = mutableStateOf("Not Connected")

    private var bluetoothGatt: BluetoothGatt? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafePi_AndroidTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Pass LocalContext.current and remove the last boolean parameter
                    DeviceList(
                        context = LocalContext.current,
                        devices = devices,
                        onScanClick = { scanLeDevice() },
                        connectionStatus = connectionStatusMessage.value
                    )
                }
            }
        }
    }

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let { scanResult ->
                if (devices.none { it.device.address == scanResult.device.address }) {
                    devices.add(scanResult)
                }
                if (scanResult.device.address == "D8:3A:DD:B6:7C:40" || scanResult.device.address == "SafePi") { // change this to "SafePi" but not reliable
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                            REQUEST_BLUETOOTH_CONNECT_PERMISSION
                        )
                    } else {
                        // Permissions are granted, proceed with stopping the scan and connecting
                        scanning = false
                        bluetoothLeScanner?.stopScan(this)
                        handler.removeCallbacksAndMessages(null)

                        // Use the MAC address for a more stable connection
                        val deviceAddress = scanResult.device.address
                        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                        device?.let {
                            bluetoothGatt = it.connectGatt(this@MainActivity, false, gattCallback)
                            connectionStatusMessage.value = "Connecting to SafePi..."
                        }
                    }
                }
            }
        }
    }

    private fun scanLeDevice() {
        // First, check and request BLUETOOTH_SCAN permission if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                REQUEST_BLUETOOTH_SCAN_PERMISSION
            )
        } else {
            // Stop any ongoing scan
            if (scanning) {
                bluetoothLeScanner?.stopScan(leScanCallback)
            }
            devices.clear() // Clear the devices list for a fresh start

            // Start a new scan
            handler.postDelayed({
                if (scanning) {
                    scanning = false
                    bluetoothLeScanner?.stopScan(leScanCallback)
                }
            }, SCANPERIOD)

            scanning = true
            bluetoothLeScanner?.startScan(leScanCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // Check if BLUETOOTH_CONNECT permission is granted
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // Request BLUETOOTH_CONNECT permission
                        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH_CONNECT_PERMISSION)
                    } else {
                        // Permission is already granted, proceed with discovering services
                        gatt?.discoverServices()
                        connectionStatusMessage.value = "Connected to SafePi"
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectionStatusMessage.value = "Disconnected"
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt?.getService(UUID.fromString("A07498CA-AD5B-474E-940D-16F1FBE7E8CD"))
                    ?.getCharacteristic(UUID.fromString("51FF12BB-3ED8-46E5-B4F9-D64E2FEC021B"))
                    ?.let { characteristic ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH_CONNECT_PERMISSION)
                            } else {
                                gatt.readCharacteristic(characteristic)
                            }
                        } else {
                            gatt.readCharacteristic(characteristic)
                        }
                    }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Successfully read the characteristic
                val data = characteristic?.value
                Log.d("MainActivity", "Characteristic read successfully: ${data?.let { Arrays.toString(it) }}")
            } else {
                Log.w("MainActivity", "Characteristic read failed: $status")
            }
        }

        // Implement other callback methods such as onCharacteristicWrite, onCharacteristicChanged, etc.
    }

    private fun checkPermissionsAndStartScan() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN
        )

        // Add BLUETOOTH_CONNECT permission for Android 12 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQUEST_BLUETOOTH_CONNECT_PERMISSION)
        } else {
            scanLeDevice() // Permissions are already granted, start scanning
        }
    }

    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT_PERMISSION = 1
        private const val REQUEST_BLUETOOTH_SCAN_PERMISSION = 1
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_BLUETOOTH_CONNECT_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted, you can retry the operation that required permission
                    scanLeDevice() // Assuming this is the method you call to start scanning
                } else {
                    // Permission denied, handle the failure
                    // Show an explanation to the user, or disable the functionality that requires the permission
                    Toast.makeText(this, "Bluetooth permission is required to scan for devices.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}

object AESUtils {

    private const val AES_MODE = "AES/CBC/PKCS5Padding"
    private const val CHARSET = "UTF-8"

    // Replace "yourEncryptionKey" and "yourInitVector" with actual values
    private const val ENCRYPTION_KEY = "yourEncryptionKey"
    private const val INIT_VECTOR = "yourInitVector"

    fun encrypt(message: String): ByteArray {
        val secretKeySpec = SecretKeySpec(ENCRYPTION_KEY.toByteArray(charset(CHARSET)), "AES")
        val ivParameterSpec = IvParameterSpec(INIT_VECTOR.toByteArray(charset(CHARSET)))

        val cipher = Cipher.getInstance(AES_MODE).apply {
            init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
        }

        return cipher.doFinal(message.toByteArray(charset(CHARSET)))
    }

    fun decrypt(encryptedMessage: ByteArray): String {
        val secretKeySpec = SecretKeySpec(ENCRYPTION_KEY.toByteArray(charset(CHARSET)), "AES")
        val ivParameterSpec = IvParameterSpec(INIT_VECTOR.toByteArray(charset(CHARSET)))

        val cipher = Cipher.getInstance(AES_MODE).apply {
            init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
        }

        val original = cipher.doFinal(encryptedMessage)

        return String(original, charset(CHARSET))
    }
}

@Composable
fun DeviceList(
    context: Context, // Add context parameter
    devices: List<ScanResult>,
    onScanClick: () -> Unit,
    connectionStatus: String
) {
    Column {
        Button(onClick = onScanClick) {
            Text("Scan")
        }
        Text(connectionStatus)
        devices.forEach { device ->
            // Check for Bluetooth connect permission before accessing device name
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            val displayName = if (hasPermission) device.device.name ?: "Unnamed" else "Unnamed"
            Text("Device: $displayName - ${device.device.address}")
        }
    }
}




