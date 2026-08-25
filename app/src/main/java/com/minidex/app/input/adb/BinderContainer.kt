package com.minidex.app.input.adb

import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable

class BinderContainer(val binder: IBinder) : Parcelable {
    private constructor(parcel: Parcel) : this(
        requireNotNull(parcel.readStrongBinder()) { "Missing mouse service binder" }
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeStrongBinder(binder)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<BinderContainer> {
        override fun createFromParcel(parcel: Parcel) = BinderContainer(parcel)
        override fun newArray(size: Int): Array<BinderContainer?> = arrayOfNulls(size)
    }
}
