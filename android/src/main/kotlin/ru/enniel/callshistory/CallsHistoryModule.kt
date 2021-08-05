package ru.enniel.callshistory

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Base64
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import kotlinx.coroutines.*
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class CallsHistoryModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    override fun getName(): String {
        return "CallsHistory"
    }

    @ReactMethod
    fun loadAll(promise: Promise) = runBlocking {
        Log.d(TAG, "loadAll")
        load(-1, promise)
    }

    @ReactMethod
    fun load(limit: Int, promise: Promise) = runBlocking {
        Log.d(TAG, "load")
        loadWithCursor(limit, null, promise)
    }

    @ReactMethod
    fun loadWithCursor(limit: Int, filter: ReadableMap?, promise: Promise) = runBlocking {
        Log.d(TAG, "loadWithCursor")
        try {
            val calls = loadCalls(limit, filter)

            val items = Arguments.createArray()
            calls.items.forEach {
                val item = Arguments.createMap()
                item.putString("phoneNumber", it.phoneNumber)
                item.putInt("duration", it.duration)
                item.putString("name", it.name)
                item.putString("photoUri", it.photoUri)
                item.putString("timestamp", it.timestamp)
                item.putString("time", it.time)
                item.putString("type", it.type)
                item.putString("cursor", it.cursor)
                items.pushMap(item)
            }

            val pagination = Arguments.createMap()
            pagination.putString("before", calls.pagination.before)
            pagination.putString("after", calls.pagination.after)

            val result = Arguments.createMap()
            result.putArray("items", items)
            result.putMap("pagination", pagination)

            promise.resolve(result)
        } catch (e: JSONException) {
            Log.e(TAG, e.message, e)
            promise.reject(e)
        }
    }

    private var callLogObserver: ContentObserver? = null

    @ReactMethod
    fun registerOnChangeListener() {
        Log.d(TAG, "registerOnChangeListener")
        callLogObserver = object : ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                sendEvent("CallsHistoryChangeData", null)
            }
        }
        reactApplicationContext.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, callLogObserver!!)
    }

    @ReactMethod
    fun removeOnChangeListener() {
        Log.d(TAG, "removeOnChangeListener")
        callLogObserver?.let { reactApplicationContext.contentResolver.unregisterContentObserver(it) }
    }

    private fun sendEvent(eventName: String, params: WritableMap?) {
        Log.d(TAG, "sendEvent")
        reactApplicationContext
            .getJSModule(RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    private suspend fun loadCalls(limit: Int, filter: ReadableMap?) = coroutineScope {
        Log.d(TAG, "loadCalls")
        val asyncTask = async(Dispatchers.IO) {
            var projection = arrayOf(
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                projection = projection.plus(CallLog.Calls.CACHED_PHOTO_URI)
            }

            var selection: String? = null
            var selectionArgs: Array<String>? = null
            val paginationCursorBase64Str = filter?.getString("cursor")
            if (paginationCursorBase64Str != null) {
                val paginationCursorStr = Base64.decode(paginationCursorBase64Str, Base64.URL_SAFE or Base64.NO_WRAP).toString(Charsets.UTF_8)
                val paginationCursorObj = JSONObject(paginationCursorStr)

                val timestamp = paginationCursorObj.getString("timestamp")
                val direction = paginationCursorObj.getString("direction")

                if (direction == Direction.AFTER) {
                    selection = "${CallLog.Calls.DATE} < ?"
                }
                if (direction == Direction.BEFORE) {
                    selection = "${CallLog.Calls.DATE} > ?"
                }
                selectionArgs = arrayOf(timestamp)
            }

            var sort = "${CallLog.Calls.DATE} DESC"
            if (limit > -1) {
                sort += " LIMIT $limit"
            }
            val cursor = reactApplicationContext.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, selection, selectionArgs, sort)
            val result = CallLogResult()
            if (cursor == null) {
                return@async result
            }

            val numberColumnIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val typeColumnIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val dateColumnIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
            val durationColumnIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
            val nameColumnIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)

            while (cursor.moveToNext()) {
                val phoneNumber = cursor.getString(numberColumnIndex)?.trim()
                if (phoneNumber != null && phoneNumber.isNotEmpty()) {
                    val timestampStr = cursor.getString(dateColumnIndex)
                    val type = resolveCallType(cursor.getInt(typeColumnIndex))
                    val duration = cursor.getInt(durationColumnIndex)
                    var name = cursor.getString(nameColumnIndex)

                    var photoUri: String? = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val photoUriColumnIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)
                        photoUri = cursor.getString(photoUriColumnIndex)
                    }

                    if (name == null || photoUri == null) {
                        val contact = getContactByPhoneNumber(phoneNumber)
                        if (contact != null) {
                            name = contact.name ?: name
                            photoUri = contact.photoUri ?: photoUri
                        }
                    }

                    val cursorJSON = JSONObject()
                    cursorJSON.put("timestamp", timestampStr)
                    val itemCursor = Base64.encodeToString(cursorJSON.toString().toByteArray(), Base64.DEFAULT)

                    val df = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val time = df.format(Date(timestampStr.toLong()))

                    val item = CallLogItem(
                        phoneNumber = phoneNumber,
                        duration = duration,
                        name = name,
                        photoUri = photoUri,
                        timestamp = timestampStr,
                        time = time,
                        type = type,
                        cursor = itemCursor
                    )
                    result.items.add(item)
                    if (cursor.isLast) {
                        val afterCursorJSON = JSONObject()
                        afterCursorJSON.put("timestamp", timestampStr)
                        afterCursorJSON.put("direction", Direction.AFTER)
                        result.pagination.after = Base64.encodeToString(afterCursorJSON.toString().toByteArray(), Base64.DEFAULT)
                    }
                    if (cursor.isFirst) {
                        val beforeCursorJSON = JSONObject()
                        beforeCursorJSON.put("timestamp", timestampStr)
                        beforeCursorJSON.put("direction", Direction.BEFORE)
                        result.pagination.before = Base64.encodeToString(beforeCursorJSON.toString().toByteArray(), Base64.DEFAULT)
                    }
                }
            }
            cursor.close()
            return@async result
        }

        return@coroutineScope asyncTask.await()
    }

    private fun resolveCallType(callTypeCode: Int): String {
        Log.d(TAG, "resolveCallType")
        return when (callTypeCode) {
            CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
            CallLog.Calls.INCOMING_TYPE -> "INCOMING"
            CallLog.Calls.MISSED_TYPE -> "MISSED"
            else -> "UNKNOWN"
        }
    }

    private fun getContactByPhoneNumber(phone: String): Contact? {
        Log.d(TAG, "getContactByPhoneNumber -> phone -> $phone")

        val res = reactContext.checkCallingOrSelfPermission(Manifest.permission.READ_CONTACTS)
        if (res == PackageManager.PERMISSION_DENIED) {
            return null
        }

        val projection = arrayOf(
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.PHOTO_URI,
        )
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
        val contentResolver = reactContext.contentResolver
        val contactLookup: Cursor? = contentResolver.query(uri, projection, null, null, null)
        contactLookup.use {
            if (it != null && it.count > 0) {
                it.moveToNext()
                val name = it.getString(it.getColumnIndex(ContactsContract.Data.DISPLAY_NAME))
                val photoUri = it.getString(it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI))
                return Contact(name, photoUri, phone)
            }
        }
        return null
    }

    data class CallLogItem(
        val phoneNumber: String,
        val duration: Int,
        val name: String?,
        val photoUri: String?,
        val timestamp: String,
        val time: String,
        val type: String,
        val cursor: String
    )

    class CallLogResult {
        val items = arrayListOf<CallLogItem>()

        val pagination = CallLogResultPagination()
    }

    class CallLogResultPagination {
        var after: String? = null

        var before: String? = null
    }

    class Direction {
        companion object {
            const val BEFORE = "before"

            const val AFTER = "after"
        }
    }

    data class Contact(
        val name: String?,
        val photoUri: String?,
        val phone: String
    )

    companion object {
        private const val TAG = "CallsHistoryModule"
    }
}