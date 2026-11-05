package com.angelvpn.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.angelvpn.app.domain.Engine
import com.angelvpn.app.domain.Profile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "angel_profiles")

class ProfileStore(private val context: Context) {
    private val gson = Gson()
    private val KEY = stringPreferencesKey("profiles_json")

    val profiles: Flow<List<Profile>> = context.profileDataStore.data.map { prefs ->
        val json = prefs[KEY] ?: "[]"
        try {
            val type = object : TypeToken<List<ProfileDto>>() {}.type
            val list: List<ProfileDto> = gson.fromJson(json, type) ?: emptyList()
            list.map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveAll(list: List<Profile>) {
        context.profileDataStore.edit { prefs ->
            val dtos = list.map { ProfileDto.from(it) }
            prefs[KEY] = gson.toJson(dtos)
        }
    }

    suspend fun upsert(profile: Profile) {
        context.profileDataStore.edit { prefs ->
            val current = loadSync(prefs)
            val next = current.filter { it.id != profile.id } + profile.copy(updatedAt = System.currentTimeMillis())
            prefs[KEY] = gson.toJson(next.map { ProfileDto.from(it) })
        }
    }

    suspend fun delete(id: String) {
        context.profileDataStore.edit { prefs ->
            val current = loadSync(prefs)
            prefs[KEY] = gson.toJson(current.filter { it.id != id }.map { ProfileDto.from(it) })
        }
    }

    suspend fun clear() {
        context.profileDataStore.edit { it.remove(KEY) }
    }

    private fun loadSync(prefs: Preferences): List<Profile> {
        val json = prefs[KEY] ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ProfileDto>>() {}.type
            val list: List<ProfileDto> = gson.fromJson(json, type) ?: emptyList()
            list.map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/** Serializable DTO to avoid enum issues in Gson */
data class ProfileDto(
    val id: String,
    val name: String,
    val engine: String,
    val subtype: String?,
    val domain: String,
    val publicKey: String?,
    val mkey: String?,
    val resolvers: List<String>,
    val pipe: List<String>?,
    val rawLink: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastConnectedAt: Long?,
    val fingerprint: String
) {
    fun toDomain() = Profile(
        id = id,
        name = name,
        engine = Engine.entries.firstOrNull { it.name == engine } ?: Engine.UNKNOWN,
        subtype = subtype,
        domain = domain,
        publicKey = publicKey,
        mkey = mkey,
        resolvers = resolvers,
        pipe = pipe,
        rawLink = rawLink,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastConnectedAt = lastConnectedAt,
        fingerprint = fingerprint
    )

    companion object {
        fun from(p: Profile) = ProfileDto(
            id = p.id,
            name = p.name,
            engine = p.engine.name,
            subtype = p.subtype,
            domain = p.domain,
            publicKey = p.publicKey,
            mkey = p.mkey,
            resolvers = p.resolvers,
            pipe = p.pipe,
            rawLink = p.rawLink,
            notes = p.notes,
            createdAt = p.createdAt,
            updatedAt = p.updatedAt,
            lastConnectedAt = p.lastConnectedAt,
            fingerprint = p.fingerprint
        )
    }
}
