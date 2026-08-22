package com.example.mdpg26.ui.devicepicker

import com.example.mdpg26.bluetooth.BtDevice

/** Row types rendered inside the device picker's single RecyclerView. */
sealed class DeviceListItem {
    data class Header(val title: String, val showProgress: Boolean) : DeviceListItem()
    data class Device(val device: BtDevice) : DeviceListItem()
    data class Empty(val message: String) : DeviceListItem()
    data class Action(val label: String, val address: String) : DeviceListItem()
}
