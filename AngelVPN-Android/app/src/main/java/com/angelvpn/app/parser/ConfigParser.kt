package com.angelvpn.app.parser

import android.util.Base64
import com.angelvpn.app.domain.Engine
import com.angelvpn.app.domain.ImportResult
import com.angelvpn.app.domain.Profile
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Unified parser for:
 *  - slipnet:// (v30 pipe, base64)
 *  - dnst:// / dnstt://
 *  - soar://
 *  - cotten:// / cottendns://
 *  - masterdns:// / stormdns://
 *  - angelvpn:// (our own)
 *  - raw JSON / TOML-ish / base64 subscription blobs
 */
object ConfigParser {

    private val SCHEME_REGEX = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*)://""")
    private val DOMAIN_REGEX = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""")

    fun parse(input: String): ImportResult {
        val text = input.trim()
        if (text.isBlank()) return ImportResult(emptyList(), errors = listOf("متن خالی است"))

        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val profiles = mutableListOf<Profile>()

        // Multi-line / subscription style
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
        if (lines.size > 1) {
            lines.forEach { line ->
                val r = parseSingle(line)
                profiles += r.profiles
                warnings += r.warnings
                errors += r.errors
            }
            return ImportResult(dedupe(profiles), warnings, errors)
        }

        // Single blob – try unwrap base64 first
        val unwrapped = tryUnwrapBase64(text)
        return parseSingle(unwrapped ?: text).also {
            if (unwrapped != null && unwrapped != text) {
                warnings += "محتوای base64 باز شد"
            }
        }
    }

    private fun parseSingle(raw: String): ImportResult {
        val warnings = mutableListOf<String>()
        val text = raw.trim()

        val schemeMatch = SCHEME_REGEX.find(text)
        if (schemeMatch != null) {
            val scheme = schemeMatch.groupValues[1].lowercase()
            val body = text.substring(schemeMatch.range.last + 1)
            return when (Engine.fromScheme(scheme)) {
                Engine.SLIPNET -> parseSlipnet(scheme, body, text, warnings)
                Engine.DNSTT -> parseDnst(scheme, body, text, warnings)
                Engine.SOAR -> parseSoar(scheme, body, text, warnings)
                Engine.COTTEN -> parseCotten(scheme, body, text, warnings)
                Engine.MASTERDNS -> parseMasterDns(scheme, body, text, warnings)
                else -> {
                    if (scheme == "angelvpn") parseAngel(body, text, warnings)
                    else ImportResult(emptyList(), errors = listOf("اسکیمای ناشناخته: $scheme"))
                }
            }
        }

        // JSON?
        if (text.startsWith("{") || text.startsWith("[")) {
            return parseJson(text, warnings)
        }

        // TOML-ish key=value blocks
        if (text.contains("=") && (text.contains("domain") || text.contains("pubkey") || text.contains("mkey"))) {
            return parseTomlLike(text, warnings)
        }

        return ImportResult(emptyList(), errors = listOf("فرمت قابل تشخیص نیست"))
    }

    // ───────────────────────── SlipNet ─────────────────────────

    private fun parseSlipnet(scheme: String, body: String, raw: String, warnings: MutableList<String>): ImportResult {
        // slipnet://BASE64  or  slipnet://v30|field0|field1|...
        val decoded = try {
            if (body.contains("|")) body
            else {
                val bytes = b64Decode(body)
                String(bytes, StandardCharsets.UTF_8)
            }
        } catch (e: Exception) {
            return ImportResult(emptyList(), errors = listOf("رمزگشایی slipnet ناموفق: ${e.message}"))
        }

        val fields = decoded.split("|").map { it.trim() }
        if (fields.isEmpty()) return ImportResult(emptyList(), errors = listOf("فیلدهای slipnet خالی"))

        // Common layout (v30-ish): index 0 = version/mode, later slots hold name/domain/pubkey
        val versionOrMode = fields.getOrNull(0) ?: ""
        val name = fields.getOrNull(1)?.ifBlank { null }
            ?: fields.getOrNull(2)?.ifBlank { null }
            ?: "SlipNet"
        val domain = findDomain(fields) ?: fields.getOrNull(3) ?: ""
        val pubkey = findPubkey(fields)
        val subtype = detectSlipnetSubtype(versionOrMode, fields)

        if (domain.isBlank()) warnings += "دامنه تونل پیدا نشد"
        if (pubkey.isNullOrBlank()) warnings += "کلید عمومی (pubkey) پیدا نشد"

        val profile = Profile(
            name = name.ifBlank { domain.ifBlank { "SlipNet Profile" } },
            engine = Engine.SLIPNET,
            subtype = subtype,
            domain = domain,
            publicKey = pubkey,
            pipe = fields,
            rawLink = raw,
            fingerprint = fingerprint("slipnet", domain, pubkey, fields.joinToString("|"))
        )
        return ImportResult(listOf(profile), warnings)
    }

    private fun detectSlipnetSubtype(mode: String, fields: List<String>): String {
        val m = mode.lowercase()
        return when {
            m.contains("soar") || fields.any { it.contains("soar", true) } -> "soar"
            m.contains("slipstream") || m.contains("ss") -> "slipstream"
            m.contains("noiz") -> "noizdns"
            m.contains("vay") -> "vaydns"
            m.contains("dnstt") || m == "30" || m == "v30" -> "dnstt"
            else -> "dnstt"
        }
    }

    // ───────────────────────── DNSTT ─────────────────────────

    private fun parseDnst(scheme: String, body: String, raw: String, warnings: MutableList<String>): ImportResult {
        // dnst://domain?key=PUBKEY&name=...
        // or dnst://BASE64JSON
        val asJson = tryUnwrapBase64(body)
        if (asJson != null && (asJson.startsWith("{") || asJson.contains("\"domain\""))) {
            return parseJson(asJson, warnings, forceEngine = Engine.DNSTT, rawLink = raw)
        }

        val (hostPart, query) = splitQuery(body)
        val params = parseQuery(query)
        val domain = hostPart.substringBefore("/").ifBlank { params["domain"] ?: params["server"] ?: "" }
        val pubkey = params["key"] ?: params["pubkey"] ?: params["public_key"] ?: params["publicKey"]
        val name = params["name"] ?: params["remark"] ?: domain.ifBlank { "DNSTT" }
        val resolvers = (params["dns"] ?: params["resolver"] ?: params["resolvers"] ?: "")
            .split(",", " ", ";").map { it.trim() }.filter { it.isNotBlank() }

        if (domain.isBlank()) warnings += "دامنه DNSTT خالی است"
        if (pubkey.isNullOrBlank()) warnings += "کلید عمومی DNSTT پیدا نشد"

        val profile = Profile(
            name = name,
            engine = Engine.DNSTT,
            subtype = "dnstt",
            domain = domain,
            publicKey = pubkey,
            resolvers = resolvers,
            rawLink = raw,
            fingerprint = fingerprint("dnstt", domain, pubkey)
        )
        return ImportResult(listOf(profile), warnings)
    }

    // ───────────────────────── Soar ─────────────────────────

    private fun parseSoar(scheme: String, body: String, raw: String, warnings: MutableList<String>): ImportResult {
        val asJson = tryUnwrapBase64(body)
        if (asJson != null && asJson.startsWith("{")) {
            return parseJson(asJson, warnings, forceEngine = Engine.SOAR, rawLink = raw)
        }
        val (hostPart, query) = splitQuery(body)
        val params = parseQuery(query)
        val domain = hostPart.ifBlank { params["domain"] ?: params["server"] ?: "" }
        val pubkey = params["key"] ?: params["pubkey"] ?: params["public_key"]
        val name = params["name"] ?: domain.ifBlank { "Soar" }
        val resolvers = (params["resolvers"] ?: params["dns"] ?: "")
            .split(",", " ").map { it.trim() }.filter { it.isNotBlank() }

        val profile = Profile(
            name = name,
            engine = Engine.SOAR,
            subtype = "soar",
            domain = domain,
            publicKey = pubkey,
            resolvers = resolvers,
            rawLink = raw,
            fingerprint = fingerprint("soar", domain, pubkey)
        )
        if (domain.isBlank()) warnings += "دامنه Soar خالی است"
        return ImportResult(listOf(profile), warnings)
    }

    // ───────────────────────── Cotten ─────────────────────────

    private fun parseCotten(scheme: String, body: String, raw: String, warnings: MutableList<String>): ImportResult {
        val asJson = tryUnwrapBase64(body)
        if (asJson != null && (asJson.startsWith("{") || asJson.contains("="))) {
            return if (asJson.startsWith("{")) parseJson(asJson, warnings, forceEngine = Engine.COTTEN, rawLink = raw)
            else parseTomlLike(asJson, warnings, forceEngine = Engine.COTTEN, rawLink = raw)
        }
        val (hostPart, query) = splitQuery(body)
        val params = parseQuery(query)
        val domain = hostPart.ifBlank { params["domain"] ?: params["server"] ?: "" }
        val mkey = params["mkey"] ?: params["key"] ?: params["encryption_key"] ?: params["secret"]
        val name = params["name"] ?: domain.ifBlank { "Cotten" }
        val subtype = if (params["mode"]?.contains("max", true) == true || scheme.contains("max")) "cottenmax" else "cotten"

        val profile = Profile(
            name = name,
            engine = Engine.COTTEN,
            subtype = subtype,
            domain = domain,
            mkey = mkey,
            publicKey = params["pubkey"],
            resolvers = (params["resolvers"] ?: params["dns"] ?: "").split(",", " ").map { it.trim() }.filter { it.isNotBlank() },
            rawLink = raw,
            fingerprint = fingerprint("cotten", domain, mkey)
        )
        if (domain.isBlank()) warnings += "دامنه Cotten خالی است"
        if (mkey.isNullOrBlank()) warnings += "کلید رمزنگاری (mkey) پیدا نشد"
        return ImportResult(listOf(profile), warnings)
    }

    // ───────────────────────── MasterDNS ─────────────────────────

    private fun parseMasterDns(scheme: String, body: String, raw: String, warnings: MutableList<String>): ImportResult {
        val asJson = tryUnwrapBase64(body)
        if (asJson != null && (asJson.startsWith("{") || asJson.contains("="))) {
            return if (asJson.startsWith("{")) parseJson(asJson, warnings, forceEngine = Engine.MASTERDNS, rawLink = raw)
            else parseTomlLike(asJson, warnings, forceEngine = Engine.MASTERDNS, rawLink = raw)
        }
        val (hostPart, query) = splitQuery(body)
        val params = parseQuery(query)
        val domain = hostPart.ifBlank { params["domain"] ?: params["server"] ?: "" }
        val mkey = params["mkey"] ?: params["key"] ?: params["encryption_key"]
        val name = params["name"] ?: domain.ifBlank { "MasterDNS" }

        val profile = Profile(
            name = name,
            engine = Engine.MASTERDNS,
            subtype = if (scheme.contains("storm")) "stormdns" else "masterdns",
            domain = domain,
            mkey = mkey,
            publicKey = params["pubkey"],
            resolvers = (params["resolvers"] ?: params["dns"] ?: "").split(",", " ").map { it.trim() }.filter { it.isNotBlank() },
            rawLink = raw,
            fingerprint = fingerprint("masterdns", domain, mkey)
        )
        if (domain.isBlank()) warnings += "دامنه MasterDNS خالی است"
        if (mkey.isNullOrBlank()) warnings += "کلید رمزنگاری پیدا نشد"
        return ImportResult(listOf(profile), warnings)
    }

    // ───────────────────────── AngelVPN own ─────────────────────────

    private fun parseAngel(body: String, raw: String, warnings: MutableList<String>): ImportResult {
        val asJson = tryUnwrapBase64(body) ?: body
        return parseJson(asJson, warnings, rawLink = raw)
    }

    // ───────────────────────── JSON ─────────────────────────

    private fun parseJson(
        text: String,
        warnings: MutableList<String>,
        forceEngine: Engine? = null,
        rawLink: String? = null
    ): ImportResult {
        return try {
            val gson = com.google.gson.Gson()
            val root = com.google.gson.JsonParser.parseString(text)
            val list = mutableListOf<Profile>()

            fun objToProfile(o: com.google.gson.JsonObject): Profile? {
                val engineStr = o.get("engine")?.asString
                    ?: o.get("type")?.asString
                    ?: o.get("protocol")?.asString
                    ?: forceEngine?.name?.lowercase()
                    ?: "unknown"
                val engine = forceEngine ?: Engine.entries.firstOrNull {
                    it.name.equals(engineStr, true) || it.label.equals(engineStr, true) || engineStr in it.schemes
                } ?: Engine.UNKNOWN

                val domain = o.get("domain")?.asString
                    ?: o.get("server")?.asString
                    ?: o.get("host")?.asString
                    ?: ""
                val name = o.get("name")?.asString ?: o.get("remark")?.asString ?: domain.ifBlank { engine.label }
                val pubkey = o.get("pubkey")?.asString ?: o.get("public_key")?.asString ?: o.get("publicKey")?.asString ?: o.get("key")?.asString
                val mkey = o.get("mkey")?.asString ?: o.get("encryption_key")?.asString ?: o.get("secret")?.asString
                val subtype = o.get("subtype")?.asString ?: o.get("mode")?.asString
                val resolvers = mutableListOf<String>()
                o.get("resolvers")?.asJsonArray?.forEach { resolvers += it.asString }
                o.get("dns")?.asString?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.let { resolvers += it }

                if (domain.isBlank() && pubkey.isNullOrBlank() && mkey.isNullOrBlank()) return null

                return Profile(
                    name = name,
                    engine = engine,
                    subtype = subtype,
                    domain = domain,
                    publicKey = pubkey,
                    mkey = mkey,
                    resolvers = resolvers.distinct(),
                    rawLink = rawLink,
                    fingerprint = fingerprint(engine.name.lowercase(), domain, pubkey ?: mkey)
                )
            }

            when {
                root.isJsonArray -> root.asJsonArray.forEach { el ->
                    if (el.isJsonObject) objToProfile(el.asJsonObject)?.let { list += it }
                }
                root.isJsonObject -> {
                    val o = root.asJsonObject
                    if (o.has("profiles") && o.get("profiles").isJsonArray) {
                        o.getAsJsonArray("profiles").forEach { el ->
                            if (el.isJsonObject) objToProfile(el.asJsonObject)?.let { list += it }
                        }
                    } else {
                        objToProfile(o)?.let { list += it }
                    }
                }
            }
            if (list.isEmpty()) ImportResult(emptyList(), errors = listOf("هیچ پروفایلی در JSON پیدا نشد"))
            else ImportResult(list, warnings)
        } catch (e: Exception) {
            ImportResult(emptyList(), errors = listOf("خطا در پارس JSON: ${e.message}"))
        }
    }

    // ───────────────────────── TOML-like ─────────────────────────

    private fun parseTomlLike(
        text: String,
        warnings: MutableList<String>,
        forceEngine: Engine? = null,
        rawLink: String? = null
    ): ImportResult {
        val map = mutableMapOf<String, String>()
        text.lines().forEach { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("[")) return@forEach
            val idx = t.indexOf('=')
            if (idx > 0) {
                val k = t.substring(0, idx).trim().lowercase().removeSurrounding("\"")
                val v = t.substring(idx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                map[k] = v
            }
        }
        val domain = map["domain"] ?: map["server"] ?: map["host"] ?: ""
        val pubkey = map["pubkey"] ?: map["public_key"] ?: map["publickey"] ?: map["key"]
        val mkey = map["mkey"] ?: map["encryption_key"] ?: map["secret"]
        val name = map["name"] ?: map["remark"] ?: domain.ifBlank { "Imported" }
        val engine = forceEngine ?: when {
            map.containsKey("mkey") && (map["engine"]?.contains("cotten", true) == true || text.contains("cotten", true)) -> Engine.COTTEN
            map.containsKey("mkey") -> Engine.MASTERDNS
            map["engine"]?.contains("soar", true) == true -> Engine.SOAR
            else -> Engine.DNSTT
        }
        val resolvers = (map["resolvers"] ?: map["dns"] ?: "").split(",", " ", ";").map { it.trim() }.filter { it.isNotBlank() }

        val profile = Profile(
            name = name,
            engine = engine,
            subtype = map["mode"] ?: map["subtype"],
            domain = domain,
            publicKey = pubkey,
            mkey = mkey,
            resolvers = resolvers,
            rawLink = rawLink,
            fingerprint = fingerprint(engine.name.lowercase(), domain, pubkey ?: mkey)
        )
        if (domain.isBlank()) warnings += "دامنه در TOML پیدا نشد"
        return ImportResult(listOf(profile), warnings)
    }

    // ───────────────────────── helpers ─────────────────────────

    private fun tryUnwrapBase64(text: String, depth: Int = 0): String? {
        if (depth > 3) return null
        val cleaned = text.trim().replace("\\s".toRegex(), "")
        if (cleaned.length < 16) return null
        // Avoid treating normal domains/URLs as b64
        if (cleaned.contains("://") || cleaned.contains(".") && cleaned.length < 80) return null
        return try {
            val bytes = b64Decode(cleaned)
            val decoded = String(bytes, StandardCharsets.UTF_8)
            if (decoded.any { it.code < 9 && it != '\n' && it != '\r' && it != '\t' }) return null
            // Recursive unwrap
            tryUnwrapBase64(decoded, depth + 1) ?: decoded
        } catch (_: Exception) {
            null
        }
    }

    private fun b64Decode(s: String): ByteArray {
        val normalized = s.replace('-', '+').replace('_', '/')
        val pad = (4 - normalized.length % 4) % 4
        return Base64.decode(normalized + "=".repeat(pad), Base64.DEFAULT)
    }

    private fun splitQuery(body: String): Pair<String, String> {
        val q = body.indexOf('?')
        return if (q >= 0) body.substring(0, q) to body.substring(q + 1)
        else body to ""
    }

    private fun parseQuery(q: String): Map<String, String> {
        if (q.isBlank()) return emptyMap()
        return q.split("&").mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null
            else {
                val k = URLDecoder.decode(part.substring(0, i), "UTF-8")
                val v = URLDecoder.decode(part.substring(i + 1), "UTF-8")
                k to v
            }
        }.toMap()
    }

    private fun findDomain(fields: List<String>): String? {
        return fields.firstOrNull { f ->
            f.contains('.') && !f.contains(" ") && DOMAIN_REGEX.matches(f.removePrefix("*.") ) ||
                (f.contains('.') && f.length in 4..253 && !f.contains("|"))
        }
    }

    private fun findPubkey(fields: List<String>): String? {
        // typical base64 keys ~43-88 chars
        return fields.firstOrNull { f ->
            val c = f.replace("=", "")
            c.length in 40..100 && c.matches(Regex("^[A-Za-z0-9+\\/_\\-]+$"))
        }
    }

    private fun fingerprint(engine: String, domain: String?, key: String?, extra: String = ""): String {
        val src = listOf(engine, domain ?: "", key ?: "", extra).joinToString("|")
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(src.toByteArray(StandardCharsets.UTF_8))
        return hash.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun dedupe(list: List<Profile>): List<Profile> {
        val seen = mutableSetOf<String>()
        return list.filter { p ->
            val fp = p.fingerprint.ifBlank { p.id }
            if (fp in seen) false else {
                seen += fp
                true
            }
        }
    }

    /** Export back to a native-style link when possible */
    fun toNativeLink(profile: Profile): String {
        return when (profile.engine) {
            Engine.SLIPNET -> {
                val pipe = profile.pipe
                if (!pipe.isNullOrEmpty()) {
                    val joined = pipe.joinToString("|")
                    val b64 = Base64.encodeToString(joined.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
                        .trimEnd('=')
                    "slipnet://$b64"
                } else {
                    "slipnet://${profile.domain}?key=${profile.publicKey ?: ""}&name=${profile.name}"
                }
            }
            Engine.DNSTT -> {
                val q = buildString {
                    append("key=${profile.publicKey ?: ""}")
                    append("&name=${profile.name}")
                    if (profile.resolvers.isNotEmpty()) append("&dns=${profile.resolvers.joinToString(",")}")
                }
                "dnst://${profile.domain}?$q"
            }
            Engine.SOAR -> "soar://${profile.domain}?key=${profile.publicKey ?: ""}&name=${profile.name}"
            Engine.COTTEN -> "cotten://${profile.domain}?mkey=${profile.mkey ?: ""}&name=${profile.name}"
            Engine.MASTERDNS -> "masterdns://${profile.domain}?mkey=${profile.mkey ?: ""}&name=${profile.name}"
            else -> profile.rawLink ?: "angelvpn://unknown"
        }
    }
}
