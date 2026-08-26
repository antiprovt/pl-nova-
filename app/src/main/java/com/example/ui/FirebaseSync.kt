package com.example.ui

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging

object FirebaseSync {
    private const val TAG = "FirebaseSync"
    var isConnected by mutableStateOf(false)
    var isConnecting by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    private var activeListener: ListenerRegistration? = null
    private var db: FirebaseFirestore? = null
    private var isInitialized = false

    // =========================================================================
    // TU VLOŽTE SVOJE FIREBASE ÚDAJE, ABY BOLI VŠETCI POUŽÍVATELIA AUTOMATICKY PREPOJENÍ
    // (Pred stiahnutím/odoslaním aplikácie stačí tieto hodnoty raz vyplniť hneď pod týmto riadkom):
    // =========================================================================
    const val DEFAULT_API_KEY = "AIzaSyCcyb0VcjpFQXj5gboYzSmdpp2OcSlZ92U"
    const val DEFAULT_PROJECT_ID = "sichter-601aa"
    const val DEFAULT_AUTH_DOMAIN = "sichter-601aa.firebaseapp.com"         // Napr: "projekt-id.firebaseapp.com"
    const val DEFAULT_STORAGE_BUCKET = "sichter-601aa.firebasestorage.app"      // Napr: "projekt-id.appspot.com"
    const val DEFAULT_MESSAGING_SENDER_ID = "82354181740"  // Napr: "123456789012"
    const val DEFAULT_APP_ID = "1:82354181740:android:66af8d389dec89e3412a49"              // Napr: "1:123456789012:android:abcdef123456"
    // =========================================================================

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        
        // Try to connect automatically if config exists in SharedPreferences
        val prefs = context.getSharedPreferences("firebase_prefs", Context.MODE_PRIVATE)
        var apiKey = prefs.getString("apiKey", "") ?: ""
        var projectId = prefs.getString("projectId", "") ?: ""
        var authDomain = prefs.getString("authDomain", "") ?: ""
        var storageBucket = prefs.getString("storageBucket", "") ?: ""
        var messagingSenderId = prefs.getString("messagingSenderId", "") ?: ""
        var appId = prefs.getString("appId", "") ?: ""

        // Ak užívateľ nemá nastavenia uložené, ale sú definované predvolené statické údaje:
        if (apiKey.isEmpty() && DEFAULT_API_KEY.isNotEmpty()) {
            apiKey = DEFAULT_API_KEY
            projectId = DEFAULT_PROJECT_ID
            authDomain = DEFAULT_AUTH_DOMAIN.ifEmpty { "$projectId.firebaseapp.com" }
            storageBucket = DEFAULT_STORAGE_BUCKET.ifEmpty { "$projectId.appspot.com" }
            messagingSenderId = DEFAULT_MESSAGING_SENDER_ID
            appId = DEFAULT_APP_ID
        }

        if (apiKey.isNotEmpty() && projectId.isNotEmpty()) {
            connect(
                context, apiKey, projectId, 
                authDomain,
                storageBucket,
                messagingSenderId,
                appId
            )
        }
    }

    fun connect(
        context: Context,
        apiKey: String,
        projectId: String,
        authDomain: String,
        storageBucket: String,
        messagingSenderId: String,
        appId: String
    ): Boolean {
        isConnecting = true
        errorMessage = null
        try {
            val builder = FirebaseOptions.Builder()
                .setApiKey(apiKey.trim())
                .setProjectId(projectId.trim())
                .setApplicationId(appId.trim().ifEmpty { "1:${messagingSenderId.trim().ifEmpty { "123456" }}:android:dummy" })

            if (messagingSenderId.trim().isNotEmpty()) {
                builder.setGcmSenderId(messagingSenderId.trim())
            }
            if (storageBucket.trim().isNotEmpty()) {
                builder.setStorageBucket(storageBucket.trim())
            }

            // Obtain or initialize FirebaseApp context
            val app = if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context.applicationContext, builder.build())
            } else {
                try {
                    FirebaseApp.getInstance()
                } catch (e: Exception) {
                    FirebaseApp.initializeApp(context.applicationContext, builder.build())
                }
            }

            db = FirebaseFirestore.getInstance(app)
            
            try {
                val auth = FirebaseAuth.getInstance(app)
                if (auth.currentUser == null) {
                    auth.signInAnonymously()
                        .addOnSuccessListener {
                            Log.d(TAG, "Successfully signed in anonymously to FirebaseAuth")
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "FirebaseAuth anonymous sign in failed", e)
                        }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "FirebaseAuth initialization skipped", t)
            }
            
            // Disable auto-init for FCM unless valid messagingSenderId is present
            try {
                val fcm = FirebaseMessaging.getInstance()
                val hasSenderId = messagingSenderId.trim().isNotEmpty() && messagingSenderId.trim() != DEFAULT_MESSAGING_SENDER_ID
                fcm.isAutoInitEnabled = hasSenderId
                if (hasSenderId) {
                    fcm.subscribeToTopic("roster_updates")
                        .addOnCompleteListener { task ->
                            if (!task.isSuccessful) {
                                Log.w(TAG, "FCM topic subscription skipped: ${task.exception?.message}")
                            }
                        }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "FCM initialization skipped", t)
            }

            // Save settings to SharedPreferences
            context.getSharedPreferences("firebase_prefs", Context.MODE_PRIVATE).edit().apply {
                putString("apiKey", apiKey.trim())
                putString("projectId", projectId.trim())
                putString("authDomain", authDomain.trim())
                putString("storageBucket", storageBucket.trim())
                putString("messagingSenderId", messagingSenderId.trim())
                putString("appId", appId.trim())
                apply()
            }

            isConnected = true
            isConnecting = false
            
            // Trigger initial listen
            startListeningCurrentMonth(context)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            errorMessage = e.localizedMessage ?: "Unknown connection error"
            isConnected = false
            isConnecting = false
            return false
        }
    }

    fun disconnect(context: Context) {
        activeListener?.remove()
        activeListener = null
        isConnected = false
        
        context.getSharedPreferences("firebase_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun triggerRosterReload(context: Context) {
        startListeningCurrentMonth(context)
    }

    fun startListeningCurrentMonth(context: Context) {
        activeListener?.remove()
        activeListener = null

        if (db == null) {
            init(context)
        }
        val localDb = db ?: return
        val currentMonth = RosterData.activeRosterMonth
        val monthDocId = "month_2026_${String.format("%02d", currentMonth)}"

        Log.d(TAG, "Listening to Firebase for $monthDocId")

        // Listen for permissions document changes
        localDb.collection("settings").document("permissions")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val data = snapshot.data ?: return@addSnapshotListener
                val grantedList = (data["grantedUsers"] as? List<*>)?.mapNotNull { it?.toString() }
                val povereneList = (data["povereneOsoby"] as? List<*>)?.mapNotNull { it?.toString() }
                
                val prefs = context.getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                if (grantedList != null) {
                    editor.putStringSet(RosterPermissions.PREF_GRANTED_USERS, grantedList.toSet())
                }
                if (povereneList != null) {
                    editor.putStringSet(RosterPermissions.PREF_POVERENE_OSOBY, povereneList.toSet())
                }
                editor.apply()
            }

        activeListener = localDb.collection("rosters").document(monthDocId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Firestore listen notice: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val data = snapshot.data ?: return@addSnapshotListener
                    
                    val published = data["published"] as? Boolean ?: false
                    RosterData.publishedMonthsMap[currentMonth] = published
                    context.getSharedPreferences("shift_prefs", Context.MODE_PRIVATE).edit()
                        .putBoolean("roster_month_${currentMonth}_published", published)
                        .apply()

                    val topRawList = data["topEmployees"] as? List<*>
                    val bottomRawList = data["bottomEmployees"] as? List<*>

                    val parsedTop = topRawList?.mapNotNull { item ->
                        val map = item as? Map<*, *>
                        if (map != null) {
                            val stringKeyMap = map.entries.associate { it.key.toString() to it.value }
                            parseFirestoreEmployee(stringKeyMap)
                        } else null
                    } ?: emptyList()

                    val parsedBottom = bottomRawList?.mapNotNull { item ->
                        val map = item as? Map<*, *>
                        if (map != null) {
                            val stringKeyMap = map.entries.associate { it.key.toString() to it.value }
                            parseFirestoreEmployee(stringKeyMap)
                        } else null
                    } ?: emptyList()

                    val prefs = context.getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
                    val oldTopSaved = prefs.getString("roster_month_${currentMonth}_top_employees", null)
                    val oldBottomSaved = prefs.getString("roster_month_${currentMonth}_bottom_employees", null)

                    val oldTop = if (oldTopSaved != null) RosterData.deserializeEmployees(oldTopSaved) else (RosterData.monthlyTopEmployees[currentMonth] ?: emptyList())
                    val oldBottom = if (oldBottomSaved != null) RosterData.deserializeEmployees(oldBottomSaved) else (RosterData.monthlyBottomEmployees[currentMonth] ?: emptyList())

                    if (topRawList != null) {
                        RosterData.monthlyTopEmployees[currentMonth] = parsedTop
                        if (currentMonth == RosterData.activeRosterMonth) {
                            RosterData.topEmployees = parsedTop
                        }
                        
                        val serializedTop = RosterData.serializeEmployees(parsedTop)
                        prefs.edit().putString("roster_month_${currentMonth}_top_employees", serializedTop).apply()
                    }

                    if (bottomRawList != null) {
                        RosterData.monthlyBottomEmployees[currentMonth] = parsedBottom
                        if (currentMonth == RosterData.activeRosterMonth) {
                            RosterData.bottomEmployees = parsedBottom
                        }
                        
                        val serializedBottom = RosterData.serializeEmployees(parsedBottom)
                        prefs.edit().putString("roster_month_${currentMonth}_bottom_employees", serializedBottom).apply()
                    }

                    // Detect changes, synchronize with personal calendar database, and prompt notify
                    detectAndProcessChanges(context, currentMonth, parsedTop, parsedBottom, oldTop, oldBottom, oldTopSaved != null && oldBottomSaved != null)
                    
                    Log.d(TAG, "Roster loaded and merged successfully for $monthDocId")
                } else {
                    Log.d(TAG, "Firestore document $monthDocId does not exist yet")
                }
            }
    }

    private fun parseFirestoreEmployee(empMap: Map<String, Any?>): RosterEmployee {
        val name = empMap["name"] as? String ?: ""
        val totalHours = empMap["totalHours"]?.toString() ?: "0"
        
        val shiftsMap = mutableMapOf<Int, RosterCell>()
        val shiftsRaw = empMap["shifts"]
        
        if (shiftsRaw is Map<*, *>) {
            for ((key, value) in shiftsRaw) {
                val dayStr = key.toString()
                val day = dayStr.toIntOrNull() ?: continue
                val cellData = value as? Map<*, *> ?: continue
                val code = cellData["code"]?.toString()
                val hours = cellData["hours"]?.toString()
                shiftsMap[day] = RosterCell(day, code, hours)
            }
        } else if (shiftsRaw is List<*>) {
            for (item in shiftsRaw) {
                val cellData = item as? Map<*, *> ?: continue
                val day = (cellData["day"] as? Number)?.toInt() ?: continue
                val code = cellData["code"]?.toString()
                val hours = cellData["hours"]?.toString()
                shiftsMap[day] = RosterCell(day, code, hours)
            }
        }
        
        return RosterEmployee(name, totalHours, shiftsMap)
    }

    fun uploadCurrentRosterToFirestore(context: Context, monthIndex: Int = RosterData.activeRosterMonth): Boolean {
        if (db == null) {
            init(context)
        }
        val localDb = db ?: return false
        val currentMonth = monthIndex
        val monthDocId = "month_2026_${String.format("%02d", currentMonth)}"

        val (topList, bottomList) = RosterData.getEmployeesForMonth(context, currentMonth)

        val topEmployeesToSend = convertRosterToMapList(topList)
        val bottomEmployeesToSend = convertRosterToMapList(bottomList)

        val dataPayload = mapOf(
            "monthIndex" to currentMonth,
            "year" to 2026,
            "month" to currentMonth,
            "published" to (RosterData.publishedMonthsMap[currentMonth] ?: false),
            "lastUpdated" to java.time.Instant.now().toString(),
            "topEmployees" to topEmployeesToSend,
            "bottomEmployees" to bottomEmployeesToSend
        )

        localDb.collection("rosters").document(monthDocId)
            .set(dataPayload)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully uploaded roster to Firestore for month $currentMonth")
                
                // Trigger push notification request so other clients are updated
                triggerPushNotificationRequest(
                    localDb, 
                    "Aktualizácia rozpisu", 
                    "Rozpis na mesiac bol upravený."
                )
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to upload roster for month $currentMonth", it)
            }

        return true
    }

    private fun detectAndProcessChanges(
        context: Context,
        currentMonth: Int,
        newTop: List<RosterEmployee>,
        newBottom: List<RosterEmployee>,
        oldTop: List<RosterEmployee>,
        oldBottom: List<RosterEmployee>,
        hasPreviousSavedState: Boolean
    ) {
        val prefs = context.getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)

        // 1. General notification: check if ANY cell/employee is edited or added across the whole roster
        if (hasPreviousSavedState) {
            var anyRosterChanged = false
            val oldCombinedList = oldTop + oldBottom
            val newCombinedList = newTop + newBottom

            if (oldCombinedList.size != newCombinedList.size) {
                anyRosterChanged = true
            } else {
                for (oldEmployee in oldCombinedList) {
                    val newEmployee = newCombinedList.find { it.name.trim().equals(oldEmployee.name.trim(), ignoreCase = true) }
                    if (newEmployee == null) {
                        anyRosterChanged = true
                        break
                    }
                    for (day in 1..31) {
                        val oldCell = oldEmployee.shifts[day]
                        val newCell = newEmployee.shifts[day]
                        if (oldCell?.code != newCell?.code || oldCell?.hours != newCell?.hours) {
                            anyRosterChanged = true
                            break
                        }
                    }
                    if (anyRosterChanged) break
                }
            }

            if (anyRosterChanged) {
                val monthName = when (currentMonth) {
                    0 -> "December"
                    1 -> "Január"
                    2 -> "Február"
                    3 -> "Marec"
                    4 -> "Apríl"
                    5 -> "Máj"
                    6 -> "Jún"
                    7 -> "Júl"
                    8 -> "August"
                    9 -> "September"
                    10 -> "Október"
                    11 -> "November"
                    12 -> "December"
                    else -> "Mesiac"
                }
                val yearText = if (currentMonth == 0) "2025" else "2026"
                val titleGeneral = "Aktualizácia rozpisu: $monthName $yearText"
                val bodyGeneral = "Webový administrátor práve uložil zmeny v službách."
                
                RosterData.triggerRosterNotification(context, titleGeneral, bodyGeneral)
            }
        }

        // 2. Personal notification: specific to the logged-in user
        val loggedInUser = prefs.getString("logged_in_user_name", "") ?: ""
        val userName = if (loggedInUser.isNotBlank()) loggedInUser else (prefs.getString("user_name", "") ?: "")
        if (userName.isBlank()) return

        val oldCombined = oldTop + oldBottom
        val newCombined = newTop + newBottom

        val oldEmp = oldCombined.find { RosterData.isSameOfficer(it.name, userName) }
        val newEmp = newCombined.find { RosterData.isSameOfficer(it.name, userName) }

        if (newEmp != null) {
            val ym = RosterData.getYearMonthForIndex(currentMonth)
            val numDays = ym.lengthOfMonth()
            val changedDays = mutableListOf<Int>()
            
            for (day in 1..numDays) {
                val oldCell = oldEmp?.shifts?.get(day)
                val newCell = newEmp.shifts[day] ?: RosterCell(day, null, null)

                val oldCode = oldCell?.code
                val oldHours = oldCell?.hours
                val newCode = newCell.code
                val newHours = newCell.hours

                if (oldCode != newCode || oldHours != newHours) {
                    changedDays.add(day)
                }
            }

            // Always synchronize roster to Šichtér for logged-in user
            RosterData.syncRosterToSichereForUser(userName, currentMonth, context)
        }
    }

    private fun convertRosterToMapList(employees: List<RosterEmployee>): List<Map<String, Any>> {
        return employees.map { emp ->
            val shiftsMap = mutableMapOf<String, Map<String, Any?>>()
            for ((day, cell) in emp.shifts) {
                shiftsMap[day.toString()] = mapOf(
                    "day" to cell.day,
                    "code" to cell.code,
                    "hours" to cell.hours
                )
            }
            mapOf(
                "name" to emp.name,
                "totalHours" to emp.totalHours,
                "shifts" to shiftsMap
            )
        }
    }

    private fun triggerPushNotificationRequest(db: FirebaseFirestore, title: String, body: String) {
        val payload = mapOf(
            "title" to title,
            "body" to body,
            "topic" to "roster_updates",
            "createdAt" to java.time.Instant.now().toString(),
            "status" to "pending"
        )
        db.collection("fcm_requests").document()
            .set(payload)
            .addOnSuccessListener {
                Log.d(TAG, "FCM demand request registered successfully")
            }
    }

    fun syncPermissionsToFirestore(prefs: SharedPreferences) {
        val localDb = db ?: return
        val granted = prefs.getStringSet(RosterPermissions.PREF_GRANTED_USERS, emptySet()) ?: emptySet()
        val poverene = prefs.getStringSet(RosterPermissions.PREF_POVERENE_OSOBY, emptySet()) ?: emptySet()
        
        val payload = mapOf(
            "grantedUsers" to granted.toList(),
            "povereneOsoby" to poverene.toList(),
            "updatedAt" to java.time.Instant.now().toString()
        )
        
        localDb.collection("settings").document("permissions")
            .set(payload, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Permissions synced to Firestore")
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to sync permissions to Firestore", it)
            }
    }
}
