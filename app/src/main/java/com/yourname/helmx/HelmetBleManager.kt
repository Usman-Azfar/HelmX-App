package com.yourname.helmx

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

@SuppressLint("MissingPermission")
class HelmetBleManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: HelmetBleManager? = null

        fun getInstance(context: Context): HelmetBleManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HelmetBleManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private val _helmetData = MutableStateFlow(HelmetData())
    val helmetData: StateFlow<HelmetData> = _helmetData

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    // Update these UUIDs to match your Raspberry Pi BLE configuration
    // Default placeholders for custom BLE services
    private val SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
    private val CHARACTERISTIC_UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e("HelmetBleManager", "Bluetooth is disabled or not available")
            return
        }

        if (isScanning) return
        
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        
        // Filter for "HelmX" device name as specified in project report
        val filter = ScanFilter.Builder()
            .setDeviceName("HelmX") 
            .build()
            
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        isScanning = true
        _helmetData.value = _helmetData.value.copy(connectionStatus = "Searching...")
        scanner.startScan(listOf(filter), settings, scanCallback)
        
        // Stop scanning after 10 seconds if nothing found
        handler.postDelayed({
            if (isScanning) {
                stopScan()
                if (bluetoothGatt == null) {
                    _helmetData.value = _helmetData.value.copy(connectionStatus = "Device Not Found")
                }
            }
        }, 10000)
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.i("HelmetBleManager", "Found device: ${result.device.name} - ${result.device.address}")
            stopScan()
            connectToDevice(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("HelmetBleManager", "Scan failed with error: $errorCode")
            isScanning = false
            _helmetData.value = _helmetData.value.copy(connectionStatus = "Scan Failed")
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        _helmetData.value = _helmetData.value.copy(connectionStatus = "Connecting...")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("HelmetBleManager", "Connected to GATT server. Requesting MTU...")
                _helmetData.value = _helmetData.value.copy(connectionStatus = "Connected")
                
                // Request larger MTU to handle JSON strings
                gatt.requestMtu(512)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("HelmetBleManager", "Disconnected from GATT server.")
                _helmetData.value = _helmetData.value.copy(connectionStatus = "Disconnected")
                
                // Clear cache on disconnect to prevent stale data
                refreshDeviceCache(gatt)
                bluetoothGatt = null
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i("HelmetBleManager", "MTU changed to $mtu, status: $status")
            // Start service discovery AFTER MTU is set
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.i("HelmetBleManager", "onServicesDiscovered status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        Log.i("HelmetBleManager", "Characteristic found. Enabling notifications...")
                        gatt.setCharacteristicNotification(characteristic, true)
                        
                        val descriptor = characteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                        )
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            val descriptorResult = gatt.writeDescriptor(descriptor)
                            Log.i("HelmetBleManager", "Descriptor write initiated: $descriptorResult")
                        } else {
                            Log.e("HelmetBleManager", "CCCD Descriptor not found!")
                        }
                    } else {
                        Log.e("HelmetBleManager", "Characteristic $CHARACTERISTIC_UUID not found in service!")
                    }
                } else {
                    Log.e("HelmetBleManager", "Service $SERVICE_UUID not found!")
                }
            } else {
                Log.e("HelmetBleManager", "Service discovery failed with status: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleCharacteristicChange(characteristic, characteristic.value)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleCharacteristicChange(characteristic, value)
        }

        private fun handleCharacteristicChange(characteristic: BluetoothGattCharacteristic, value: ByteArray?) {
            if (characteristic.uuid == CHARACTERISTIC_UUID && value != null) {
                val dataString = String(value)
                Log.d("HelmetBleManager", "Received: $dataString")
                parseHelmetData(dataString)
            }
        }
    }

    private fun parseHelmetData(rawString: String) {
        try {
            // Clean the string of any non-printable or null characters
            val cleanString = rawString.replace(Regex("[^\\x20-\\x7E]"), "").trim()
            Log.d("HelmetBleManager", "Parsing JSON: $cleanString")

            if (cleanString.startsWith("{") && cleanString.endsWith("}")) {
                val json = org.json.JSONObject(cleanString)
                _helmetData.value = _helmetData.value.copy(
                    temperature = json.optDouble("temperature", 0.0).toFloat(),
                    humidity = json.optDouble("humidity", 0.0).toFloat(),
                    airQuality = json.optString("air_quality", "Unknown"),
                    destination = json.optString("destination", ""),
                    connectionStatus = "Connected"
                )
                Log.d("HelmetBleManager", "Updated Stats from JSON -> Temp: ${_helmetData.value.temperature}, Hum: ${_helmetData.value.humidity}, Air: ${_helmetData.value.airQuality}")
            } else {
                // Fallback to legacy CSV parsing
                val parts = cleanString.split(",").map { it.trim() }
                Log.d("HelmetBleManager", "Detected Legacy CSV format with ${parts.size} parts")
                
                if (parts.size >= 5) {
                    _helmetData.value = _helmetData.value.copy(
                        batteryLevel = parts[0].toIntOrNull() ?: 0,
                        speed = parts[1].toFloatOrNull() ?: 0f,
                        isDrowsy = parts[2] == "1" || parts[2] == "true",
                        isCrashDetected = parts[3] == "1" || parts[3] == "true",
                        distance = parts[4].toFloatOrNull() ?: 0f,
                        connectionStatus = "Connected"
                    )
                } else if (parts.size == 2) {
                    val temp = parts[0].filter { it.isDigit() || it == '.' || it == '-' }.toFloatOrNull()
                    val hum = parts[1].filter { it.isDigit() || it == '.' }.toFloatOrNull()
                    if (temp != null && hum != null) {
                        _helmetData.value = _helmetData.value.copy(
                            temperature = temp,
                            humidity = hum,
                            connectionStatus = "Connected"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("HelmetBleManager", "Error parsing data: $rawString", e)
        }
    }
    
    fun disconnect() {
        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
            refreshDeviceCache(gatt)
            gatt.close()
        }
        bluetoothGatt = null
        _helmetData.value = HelmetData()
    }

    private fun refreshDeviceCache(gatt: BluetoothGatt): Boolean {
        return try {
            val refreshMethod = gatt.javaClass.getMethod("refresh")
            val result = refreshMethod.invoke(gatt) as Boolean
            Log.d("HelmetBleManager", "GATT cache refresh result: $result")
            result
        } catch (e: Exception) {
            Log.e("HelmetBleManager", "Failed to refresh GATT cache", e)
            false
        }
    }
}
