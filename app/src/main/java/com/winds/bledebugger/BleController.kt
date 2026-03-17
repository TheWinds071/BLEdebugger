package com.winds.bledebugger

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BleController(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null
    private var receiverRegistered = false
    private var autoEnableNotifyPending = false
    private var serviceDiscoveryRetryCount = 0

    val scanResults = mutableStateListOf<BleScanItem>()
    val logs = mutableStateListOf<LogItem>()
    val gattCharacteristics = mutableStateListOf<GattCharacteristicItem>()

    var isBluetoothSupported by mutableStateOf(adapter != null)
        private set
    var isBluetoothEnabled by mutableStateOf(adapter?.isEnabled == true)
        private set
    var isScanning by mutableStateOf(false)
        private set
    var isScannerAvailable by mutableStateOf(scanner != null)
        private set
    var isClassicDiscovering by mutableStateOf(adapter?.isDiscovering == true)
        private set
    var isLocationEnabled by mutableStateOf(isLocationServiceEnabled())
        private set
    var bondedDeviceCount by mutableStateOf(0)
        private set
    var discoveryDeviceCount by mutableStateOf(0)
        private set
    var lastDiscoveryTime by mutableStateOf<String?>(null)
        private set
    var selectedDevice by mutableStateOf<BleScanItem?>(null)
        private set
    var gattConnectionState by mutableStateOf("未连接")
        private set
    var discoveredServiceCount by mutableStateOf(0)
        private set
    var selectedCharacteristicId by mutableStateOf<String?>(null)
        private set
    var writeCharacteristicId by mutableStateOf<String?>(null)
        private set
    var notifyCharacteristicId by mutableStateOf<String?>(null)
        private set
    var readCharacteristicId by mutableStateOf<String?>(null)
        private set
    var notifyEnabled by mutableStateOf(false)
        private set
    var lastGattValue by mutableStateOf<String?>(null)
        private set
    var lastTxValue by mutableStateOf<String?>(null)
        private set
    var lastWriteError by mutableStateOf<String?>(null)
        private set
    var writePayload by mutableStateOf("")
    var sendMode by mutableStateOf(SendMode.Hex)
    var currentMtu by mutableStateOf(23)
        private set
    var isGattConnected by mutableStateOf(false)
        private set
    private var pendingWriteChunks = ArrayDeque<ByteArray>()

    val selectedCharacteristicSummary: String?
        get() = gattCharacteristics.firstOrNull { it.id == selectedCharacteristicId }?.let {
            "${shortUuid(it.serviceUuid)} / ${shortUuid(it.characteristicUuid)}"
        }

    val writeCharacteristicSummary: String?
        get() = gattCharacteristics.firstOrNull { it.id == writeCharacteristicId }?.let { shortUuid(it.characteristicUuid) }

    val notifyCharacteristicSummary: String?
        get() = gattCharacteristics.firstOrNull { it.id == notifyCharacteristicId }?.let { shortUuid(it.characteristicUuid) }

    val canReadSelectedCharacteristic: Boolean
        get() = findReadCharacteristic()?.let { hasProperty(it, BluetoothGattCharacteristic.PROPERTY_READ) } == true && isGattConnected

    val canWriteSelectedCharacteristic: Boolean
        get() = findWriteCharacteristic()?.let {
            hasProperty(it, BluetoothGattCharacteristic.PROPERTY_WRITE) ||
                hasProperty(it, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
        } == true && isGattConnected

    val canToggleSelectedNotifications: Boolean
        get() = findNotifyCharacteristic()?.let {
            hasProperty(it, BluetoothGattCharacteristic.PROPERTY_NOTIFY) ||
                hasProperty(it, BluetoothGattCharacteristic.PROPERTY_INDICATE)
        } == true && isGattConnected

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    refreshState()
                    appendLog("SYS", if (isBluetoothEnabled) "蓝牙已开启" else "蓝牙已关闭")
                    if (!isBluetoothEnabled) stopScan()
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    if (device != null) {
                        updateClassicResult(device, rssi)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isClassicDiscovering = false
                    if (isScanning) appendLog("SYS", "经典蓝牙搜索结束")
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    isClassicDiscovering = true
                    appendLog("SYS", "经典蓝牙搜索开始")
                }
            }
        }
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            updateScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::updateScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            appendLog("ERR", "扫描启动失败，错误码=$errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    this@BleController.gatt = gatt
                    isGattConnected = true
                    gattConnectionState = "已连接"
                    appendLog("GATT", "GATT 已连接，准备发现服务")
                    serviceDiscoveryRetryCount = 0
                    mainHandler.postDelayed({
                        if (this@BleController.gatt === gatt && isGattConnected) {
                            startServiceDiscovery(gatt)
                        }
                    }, 300L)
                }
                BluetoothGatt.STATE_CONNECTING -> {
                    gattConnectionState = "连接中"
                }
                else -> {
                    appendLog("GATT", "GATT 已断开，status=$status")
                    clearGattState()
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                discoveredServiceCount = gatt.services.size
                updateGattCharacteristics(gatt)
                appendLog("GATT", "服务发现完成，共 ${gatt.services.size} 个服务")
                gatt.requestMtu(247)
                if (autoEnableNotifyPending && notifyCharacteristicId != null && !notifyEnabled) {
                    autoEnableNotifyPending = false
                    toggleNotifications()
                }
            } else {
                appendLog("ERR", "服务发现失败，status=$status")
                retryServiceDiscovery(gatt)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                lastGattValue = formatReceivedValue(value)
                appendLog("RX", "读取 ${shortUuid(characteristic.uuid.toString())}: ${value.toHexString()}")
            } else {
                appendLog("ERR", "读取失败，status=$status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            lastGattValue = formatReceivedValue(value)
            appendLog("NTF", "通知 ${shortUuid(characteristic.uuid.toString())}: ${value.toHexString()}")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (pendingWriteChunks.isNotEmpty()) {
                    val nextChunk = pendingWriteChunks.removeFirst()
                    writeChunk(characteristic, nextChunk)
                } else {
                    lastTxValue = writePayload
                    lastWriteError = null
                    appendLog("TX", "写入 ${shortUuid(characteristic.uuid.toString())} 成功: $writePayload")
                }
            } else {
                pendingWriteChunks.clear()
                lastWriteError = explainGattStatus(status)
                appendLog("ERR", "写入失败，status=$status，${explainGattStatus(status)}")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentMtu = mtu
                appendLog("GATT", "MTU 已更新为 $mtu")
            } else {
                appendLog("ERR", "MTU 请求失败，status=$status")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == CCCD_UUID) {
                notifyEnabled = status == BluetoothGatt.GATT_SUCCESS && notifyEnabled
                appendLog(
                    if (status == BluetoothGatt.GATT_SUCCESS) "GATT" else "ERR",
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (notifyEnabled) "通知已开启" else "通知已关闭"
                    } else {
                        "通知配置失败，status=$status"
                    }
                )
            }
        }
    }

    fun startObserving() {
        refreshState()
        loadBondedDevices()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                context,
                stateReceiver,
                IntentFilter().apply {
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
    }

    fun dispose() {
        stopScan()
        disconnectGatt()
        if (receiverRegistered) {
            context.unregisterReceiver(stateReceiver)
            receiverRegistered = false
        }
    }

    fun refreshState() {
        isBluetoothSupported = adapter != null
        isBluetoothEnabled = adapter?.isEnabled == true
        isScannerAvailable = scanner != null
        isClassicDiscovering = adapter?.isDiscovering == true
        isLocationEnabled = isLocationServiceEnabled()
        loadBondedDevices()
    }

    fun hasRequiredPermissions(): Boolean {
        return BLE_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        refreshState()
        if (!isBluetoothSupported) {
            appendLog("ERR", "当前设备不支持 BLE")
            return
        }
        if (!hasRequiredPermissions()) {
            appendLog("ERR", "缺少蓝牙权限，无法开始扫描")
            return
        }
        if (!isBluetoothEnabled) {
            appendLog("ERR", "蓝牙未开启")
            return
        }
        if (!isLocationEnabled) {
            appendLog("ERR", "定位服务未开启，很多设备上将无法扫描到附近蓝牙设备")
        }
        if (isScanning) return

        clearTransientResults()
        loadBondedDevices()
        discoveryDeviceCount = 0
        lastDiscoveryTime = null
        if (adapter?.isDiscovering == true) {
            adapter?.cancelDiscovery()
        }
        scanner?.let {
            it.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                callback
            )
            appendLog("SYS", "开始扫描 BLE 设备")
        } ?: appendLog("ERR", "BLE 扫描器不可用")
        val started = adapter?.startDiscovery() == true
        isClassicDiscovering = started
        isScanning = true
        appendLog("SYS", if (started) "开始搜索经典蓝牙设备" else "经典蓝牙搜索未启动")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (isScanning) {
            scanner?.stopScan(callback)
            if (adapter?.isDiscovering == true) {
                adapter?.cancelDiscovery()
            }
            isClassicDiscovering = false
            isScanning = false
            appendLog("SYS", "停止扫描")
        }
    }

    fun clearResults() {
        stopScan()
        scanResults.clear()
        selectedDevice = null
        loadBondedDevices()
        appendLog("SYS", "已清空扫描结果")
    }

    fun selectDevice(device: BleScanItem) {
        val previousAddress = selectedDevice?.address
        selectedDevice = device
        if (previousAddress != null && previousAddress != device.address) {
            disconnectGatt()
        }
        appendLog("SEL", "选中设备 ${device.displayName} (${device.address})")
        if (device.supportsGatt && previousAddress != device.address) {
            connectSelectedDevice(autoEnableNotify = true)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectSelectedDevice(autoEnableNotify: Boolean = false) {
        val device = selectedDevice ?: run {
            appendLog("ERR", "未选择设备")
            return
        }
        if (!device.supportsGatt) {
            appendLog("ERR", "当前设备不支持 GATT")
            return
        }
        if (!hasRequiredPermissions()) {
            appendLog("ERR", "缺少蓝牙权限，无法连接")
            return
        }
        if (isScanning) {
            stopScan()
        }
        autoEnableNotifyPending = autoEnableNotify
        disconnectGatt()
        autoEnableNotifyPending = autoEnableNotify
        gattConnectionState = "连接中"
        val remote = adapter?.getRemoteDevice(device.address)
        if (remote == null) {
            appendLog("ERR", "无法获取远端设备")
            gattConnectionState = "未连接"
            return
        }
        gatt = remote.connectGatt(
            context,
            false,
            gattCallback,
            if (device.transport == "双模") BluetoothDevice.TRANSPORT_AUTO else BluetoothDevice.TRANSPORT_LE
        )
        appendLog("GATT", "开始连接 ${device.displayName}")
    }

    @SuppressLint("MissingPermission")
    fun disconnectGatt() {
        autoEnableNotifyPending = false
        gatt?.disconnect()
        gatt?.close()
        clearGattState()
    }

    @SuppressLint("MissingPermission")
    fun discoverGattServices() {
        val activeGatt = gatt ?: return appendLog("GATT", "服务发现未启动")
        serviceDiscoveryRetryCount = 0
        startServiceDiscovery(activeGatt)
    }

    @SuppressLint("MissingPermission")
    fun readSelectedCharacteristic() {
        val characteristic = findReadCharacteristic() ?: run {
            appendLog("ERR", "未选择可读特征")
            return
        }
        val started = gatt?.readCharacteristic(characteristic) == true
        appendLog("GATT", if (started) "发起读取 ${shortUuid(characteristic.uuid.toString())}" else "读取未启动")
    }

    @SuppressLint("MissingPermission")
    fun writeSelectedCharacteristic() {
        val characteristic = findWriteCharacteristic() ?: run {
            appendLog("ERR", "未选择可写特征")
            return
        }
        val payload = when (sendMode) {
            SendMode.Hex -> parseHex(writePayload)
            SendMode.Utf8 -> writePayload.toByteArray(Charsets.UTF_8)
        } ?: run {
            appendLog("ERR", "写入数据不是合法 Hex")
            return
        }
        if (payload.isEmpty()) {
            appendLog("ERR", "发送内容为空")
            return
        }
        val chunkSize = (currentMtu - 3).coerceAtLeast(1)
        pendingWriteChunks.clear()
        payload.asList().chunked(chunkSize).map { it.toByteArray() }.forEach { pendingWriteChunks.addLast(it) }
        val firstChunk = pendingWriteChunks.removeFirst()
        val status = writeChunk(characteristic, firstChunk)
        if (status != BluetoothStatusCodes.SUCCESS) {
            pendingWriteChunks.clear()
            lastWriteError = explainGattStatus(status ?: -1)
            appendLog("ERR", "写入未启动，status=$status，${explainGattStatus(status ?: -1)}")
        }
    }

    @SuppressLint("MissingPermission")
    fun toggleNotifications() {
        val characteristic = findNotifyCharacteristic() ?: run {
            appendLog("ERR", "未选择可通知特征")
            return
        }
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            appendLog("ERR", "该特征没有 CCCD，无法订阅通知")
            return
        }
        val enable = !notifyEnabled
        gatt?.setCharacteristicNotification(characteristic, enable)
        val value = if (hasProperty(characteristic, BluetoothGattCharacteristic.PROPERTY_INDICATE)) {
            if (enable) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        } else {
            if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }
        notifyEnabled = enable
        val status = gatt?.writeDescriptor(descriptor, value)
        if (status != BluetoothStatusCodes.SUCCESS) {
            appendLog("ERR", "通知配置未启动，status=$status")
            notifyEnabled = !enable
        }
    }

    fun selectCharacteristic(id: String) {
        selectedCharacteristicId = id
    }

    fun appendLog(type: String, message: String) {
        logs.add(LogItem(timestampNow(), type, message))
        if (logs.size > 100) logs.removeAt(0)
    }

    @SuppressLint("MissingPermission")
    private fun updateScanResult(result: ScanResult) {
        upsertResult(
            BleScanItem(
                name = result.device.name,
                address = result.device.address ?: "未知地址",
                rssi = result.rssi,
                isConnectable = result.isConnectable,
                isBonded = result.device.bondState == BluetoothDevice.BOND_BONDED,
                transport = "BLE"
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun updateClassicResult(device: BluetoothDevice, rssi: Int) {
        upsertResult(
            BleScanItem(
                name = device.name,
                address = device.address ?: "未知地址",
                rssi = if (rssi == Short.MIN_VALUE.toInt()) 0 else rssi,
                isConnectable = true,
                isBonded = device.bondState == BluetoothDevice.BOND_BONDED,
                transport = when (device.type) {
                    BluetoothDevice.DEVICE_TYPE_CLASSIC -> "经典"
                    BluetoothDevice.DEVICE_TYPE_DUAL -> "双模"
                    BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
                    else -> "未知"
                }
            )
        )
    }

    private fun upsertResult(item: BleScanItem) {
        val existingIndex = scanResults.indexOfFirst { it.address == item.address }
        if (existingIndex >= 0) {
            scanResults[existingIndex] = item
        } else {
            scanResults.add(item)
            discoveryDeviceCount += 1
            lastDiscoveryTime = timestampNow()
            appendLog("ADV", "发现${item.transport}设备 ${item.displayName} (${item.address})")
        }
        val sorted = scanResults.sortedWith(compareByDescending<BleScanItem> { it.isBonded }.thenByDescending { it.rssi })
        scanResults.clear()
        scanResults.addAll(sorted)
        if (selectedDevice?.address == item.address) {
            selectedDevice = item
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadBondedDevices() {
        if (!hasRequiredPermissions() || adapter == null) return
        val bonded = adapter?.bondedDevices.orEmpty().map {
            BleScanItem(
                name = it.name,
                address = it.address ?: "未知地址",
                rssi = 0,
                isConnectable = true,
                isBonded = true,
                transport = when (it.type) {
                    BluetoothDevice.DEVICE_TYPE_CLASSIC -> "经典"
                    BluetoothDevice.DEVICE_TYPE_DUAL -> "双模"
                    BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
                    else -> "已配对"
                }
            )
        }
        bondedDeviceCount = bonded.size
        val transient = scanResults.filterNot { it.isBonded }
        scanResults.clear()
        scanResults.addAll((bonded + transient).distinctBy { it.address }.sortedWith(compareByDescending<BleScanItem> { it.isBonded }.thenByDescending { it.rssi }))
        selectedDevice?.let { selected ->
            selectedDevice = scanResults.firstOrNull { it.address == selected.address }
        }
    }

    private fun clearTransientResults() {
        val bonded = scanResults.filter { it.isBonded }
        scanResults.clear()
        scanResults.addAll(bonded)
        if (selectedDevice?.isBonded != true) {
            selectedDevice = null
        }
    }

    fun openLocationSettings() {
        context.startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun isLocationServiceEnabled(): Boolean {
        val locationManager = context.getSystemService(LocationManager::class.java)
        return locationManager?.isLocationEnabled == true
    }

    private fun updateGattCharacteristics(gatt: BluetoothGatt) {
        val items = gatt.services.flatMap { service ->
            service.characteristics.map { characteristic ->
                GattCharacteristicItem(
                    id = "${service.uuid}|${characteristic.uuid}",
                    serviceUuid = service.uuid.toString(),
                    characteristicUuid = characteristic.uuid.toString(),
                    propertiesLabel = characteristic.properties.asPropertyLabel()
                )
            }
        }
        gattCharacteristics.clear()
        gattCharacteristics.addAll(items)
        selectedCharacteristicId = items.firstOrNull()?.id
        readCharacteristicId = items.firstOrNull { isNusTxCharacteristic(it) }?.id ?: items.firstOrNull {
            findCharacteristic(it.id)?.let { c -> hasProperty(c, BluetoothGattCharacteristic.PROPERTY_READ) } == true
        }?.id
        writeCharacteristicId = items.firstOrNull { isNusRxCharacteristic(it) }?.id ?: items.firstOrNull {
            findCharacteristic(it.id)?.let { c ->
                hasProperty(c, BluetoothGattCharacteristic.PROPERTY_WRITE) ||
                    hasProperty(c, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
            } == true
        }?.id
        notifyCharacteristicId = items.firstOrNull { isNusTxCharacteristic(it) }?.id ?: items.firstOrNull {
            findCharacteristic(it.id)?.let { c ->
                hasProperty(c, BluetoothGattCharacteristic.PROPERTY_NOTIFY) ||
                    hasProperty(c, BluetoothGattCharacteristic.PROPERTY_INDICATE)
            } == true
        }?.id
        selectedCharacteristicId = writeCharacteristicId ?: notifyCharacteristicId ?: readCharacteristicId ?: selectedCharacteristicId
    }

    private fun clearGattState() {
        isGattConnected = false
        gattConnectionState = "未连接"
        discoveredServiceCount = 0
        selectedCharacteristicId = null
        writeCharacteristicId = null
        notifyCharacteristicId = null
        readCharacteristicId = null
        notifyEnabled = false
        lastGattValue = null
        lastTxValue = null
        lastWriteError = null
        currentMtu = 23
        pendingWriteChunks.clear()
        autoEnableNotifyPending = false
        gattCharacteristics.clear()
        gatt = null
    }

    private fun findWriteCharacteristic(): BluetoothGattCharacteristic? {
        val id = writeCharacteristicId ?: selectedCharacteristicId ?: return null
        return findCharacteristic(id)
    }

    private fun findNotifyCharacteristic(): BluetoothGattCharacteristic? {
        val id = notifyCharacteristicId ?: selectedCharacteristicId ?: return null
        return findCharacteristic(id)
    }

    private fun findReadCharacteristic(): BluetoothGattCharacteristic? {
        val id = readCharacteristicId ?: selectedCharacteristicId ?: return null
        return findCharacteristic(id)
    }

    private fun findCharacteristic(id: String): BluetoothGattCharacteristic? {
        val parts = id.split('|', limit = 2)
        if (parts.size != 2) return null
        return gatt?.getService(UUID.fromString(parts[0]))?.getCharacteristic(UUID.fromString(parts[1]))
    }

    private fun hasProperty(characteristic: BluetoothGattCharacteristic, property: Int): Boolean {
        return characteristic.properties and property != 0
    }

    private fun startServiceDiscovery(gatt: BluetoothGatt) {
        val started = gatt.discoverServices()
        appendLog("GATT", if (started) "开始发现服务" else "服务发现未启动")
        if (!started) retryServiceDiscovery(gatt)
    }

    private fun retryServiceDiscovery(gatt: BluetoothGatt) {
        if (serviceDiscoveryRetryCount >= 1) return
        serviceDiscoveryRetryCount += 1
        appendLog("GATT", "服务发现重试中")
        mainHandler.postDelayed({
            if (this.gatt === gatt && isGattConnected) {
                startServiceDiscovery(gatt)
            }
        }, 800L)
    }

    private fun formatReceivedValue(value: ByteArray): String {
        return if (sendMode == SendMode.Utf8) value.toString(Charsets.UTF_8) else value.toHexString()
    }

    private fun isNusRxCharacteristic(item: GattCharacteristicItem): Boolean {
        return item.serviceUuid.equals(NUS_SERVICE_UUID.toString(), true) &&
            item.characteristicUuid.equals(NUS_RX_UUID.toString(), true)
    }

    private fun isNusTxCharacteristic(item: GattCharacteristicItem): Boolean {
        return item.serviceUuid.equals(NUS_SERVICE_UUID.toString(), true) &&
            item.characteristicUuid.equals(NUS_TX_UUID.toString(), true)
    }

    @SuppressLint("MissingPermission")
    private fun writeChunk(characteristic: BluetoothGattCharacteristic, chunk: ByteArray): Int? {
        val writeType = if (hasProperty(characteristic, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        return gatt?.writeCharacteristic(characteristic, chunk, writeType)
    }
}

data class BleScanItem(
    val name: String?,
    val address: String,
    val rssi: Int,
    val isConnectable: Boolean,
    val isBonded: Boolean = false,
    val transport: String = "BLE"
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "未命名设备"
    val rssiLabel: String
        get() = if (isBonded && rssi == 0) "-- dBm" else "$rssi dBm"
    val supportsGatt: Boolean
        get() = transport == "BLE" || transport == "双模"
}

data class LogItem(
    val time: String,
    val type: String,
    val message: String
)

data class GattCharacteristicItem(
    val id: String,
    val serviceUuid: String,
    val characteristicUuid: String,
    val propertiesLabel: String
)

data class BottomTab(
    val label: String,
    @param:DrawableRes val iconRes: Int
)

enum class SendMode {
    Hex,
    Utf8
}

val BLE_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.ACCESS_FINE_LOCATION
)

val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
val NUS_RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
val NUS_TX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

fun timestampNow(): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}

fun ByteArray.toHexString(): String {
    return joinToString(" ") { "%02X".format(it) }
}

fun parseHex(input: String): ByteArray? {
    val normalized = input.replace(" ", "").uppercase(Locale.getDefault())
    if (normalized.isEmpty() || normalized.length % 2 != 0 || normalized.any { !it.isDigit() && it !in 'A'..'F' }) {
        return null
    }
    return normalized.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

fun Int.asPropertyLabel(): String {
    val labels = buildList {
        if (this@asPropertyLabel and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
        if (this@asPropertyLabel and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
        if (this@asPropertyLabel and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NR")
        if (this@asPropertyLabel and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
        if (this@asPropertyLabel and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
    }
    return if (labels.isEmpty()) "无常用属性" else labels.joinToString(" · ")
}

fun shortUuid(uuid: String): String {
    return uuid.substringBefore("-")
}

fun explainGattStatus(status: Int): String {
    return when (status) {
        13 -> "属性长度无效，通常是写入内容超过当前 MTU 可承载长度"
        BluetoothGatt.GATT_SUCCESS -> "成功"
        else -> "GATT 状态码 $status"
    }
}
