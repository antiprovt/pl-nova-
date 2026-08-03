package com.example.ui

import android.content.Context
import android.content.SharedPreferences

object RosterPermissions {
    const val PREF_GRANTED_USERS = "roster_access_granted_users"
    const val PREF_POVERENE_OSOBY = "roster_poverene_osoby"

    fun isAdminOrPoverena(userName: String, prefs: SharedPreferences): Boolean {
        val u = userName.trim().lowercase()
        if (u.isBlank()) return false
        if (u == "admin" || u == "riegert" || u == "rieger t.") return true

        val povereneSet = prefs.getStringSet(PREF_POVERENE_OSOBY, emptySet()) ?: emptySet()
        return povereneSet.any { it.trim().equals(userName.trim(), ignoreCase = true) }
    }

    fun hasRosterViewAccess(userName: String, prefs: SharedPreferences): Boolean {
        val u = userName.trim()
        if (u.isBlank()) return false
        if (isAdminOrPoverena(u, prefs)) return true

        val grantedSet = prefs.getStringSet(PREF_GRANTED_USERS, emptySet()) ?: emptySet()
        return grantedSet.any { it.trim().equals(u, ignoreCase = true) }
    }

    fun getGrantedUsers(prefs: SharedPreferences): Set<String> {
        return prefs.getStringSet(PREF_GRANTED_USERS, emptySet()) ?: emptySet()
    }

    fun getPovereneOsoby(prefs: SharedPreferences): Set<String> {
        return prefs.getStringSet(PREF_POVERENE_OSOBY, emptySet()) ?: emptySet()
    }

    fun setRosterViewAccess(userName: String, granted: Boolean, prefs: SharedPreferences) {
        val currentSet = (prefs.getStringSet(PREF_GRANTED_USERS, emptySet()) ?: emptySet()).toMutableSet()
        val existing = currentSet.find { it.trim().equals(userName.trim(), ignoreCase = true) }

        if (granted) {
            if (existing == null) {
                currentSet.add(userName.trim())
            }
        } else {
            if (existing != null) {
                currentSet.remove(existing)
            }
        }

        prefs.edit().putStringSet(PREF_GRANTED_USERS, currentSet).apply()
        FirebaseSync.syncPermissionsToFirestore(prefs)
    }

    fun setPoverenaOsoba(userName: String, isPoverena: Boolean, prefs: SharedPreferences) {
        val currentSet = (prefs.getStringSet(PREF_POVERENE_OSOBY, emptySet()) ?: emptySet()).toMutableSet()
        val existing = currentSet.find { it.trim().equals(userName.trim(), ignoreCase = true) }

        if (isPoverena) {
            if (existing == null) {
                currentSet.add(userName.trim())
            }
        } else {
            if (existing != null) {
                currentSet.remove(existing)
            }
        }

        prefs.edit().putStringSet(PREF_POVERENE_OSOBY, currentSet).apply()
        FirebaseSync.syncPermissionsToFirestore(prefs)
    }

    fun grantAll(allUserNames: List<String>, prefs: SharedPreferences) {
        val currentSet = (prefs.getStringSet(PREF_GRANTED_USERS, emptySet()) ?: emptySet()).toMutableSet()
        for (name in allUserNames) {
            if (name.isNotBlank() && !currentSet.any { it.trim().equals(name.trim(), ignoreCase = true) }) {
                currentSet.add(name.trim())
            }
        }
        prefs.edit().putStringSet(PREF_GRANTED_USERS, currentSet).apply()
        FirebaseSync.syncPermissionsToFirestore(prefs)
    }

    fun revokeAll(prefs: SharedPreferences) {
        prefs.edit().putStringSet(PREF_GRANTED_USERS, emptySet()).apply()
        FirebaseSync.syncPermissionsToFirestore(prefs)
    }
}
