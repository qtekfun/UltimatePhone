package com.qtekfun.ultimatephone.feature.onboarding

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The system settings screens a guide can open. Each topic of the guide has its own list, tried in order. */
object GuideTopics {
    const val BATTERY = "battery"
    const val AUTOSTART = "autostart"
    const val POPUP = "popup"
    const val FULLSCREEN = "fullscreen"

    val all = listOf(BATTERY, AUTOSTART, POPUP, FULLSCREEN)
}

/**
 * One candidate screen to open: an [action], an explicit [component] (`package/class`), a [data] URI, or a mix. `{package}` in
 * [data] and in the values of [extras] is replaced by this app's package name. Nothing assumes the screen exists:
 * the launcher tries each candidate and moves on when the device cannot open it.
 */
@Serializable
data class IntentSpec(val action: String? = null, val component: String? = null, val data: String? = null, val extras: Map<String, String> = emptyMap()) {
    val componentPackage: String? get() = component?.substringBefore('/')?.takeIf { it.isNotEmpty() && component.contains('/') }
    val componentClass: String? get() = component?.substringAfter('/', "")?.takeIf { it.isNotEmpty() }

    /** A spec with neither action nor component cannot start anything. */
    val isUsable: Boolean get() = action != null || (componentPackage != null && componentClass != null)

    fun dataFor(packageName: String): String? = data?.replace(PACKAGE_PLACEHOLDER, packageName)

    fun extrasFor(packageName: String): Map<String, String> = extras.mapValues { it.value.replace(PACKAGE_PLACEHOLDER, packageName) }

    private companion object {
        const val PACKAGE_PLACEHOLDER = "{package}"
    }
}

/** What to tell the user ([textKey] names a string resource) and where to send them ([intents]). */
@Serializable
data class GuideTopic(val textKey: String, val intents: List<IntentSpec> = emptyList())

@Serializable
data class OemGuide(val id: String, val manufacturers: List<String> = emptyList(), val topics: Map<String, GuideTopic> = emptyMap())

@Serializable
data class OemGuideFile(val schema: Int = 1, val guides: List<OemGuide> = emptyList())

/** A topic ready to show: the text key and every screen to try, the guide's own first and the generic ones after. */
data class ResolvedTopic(val topic: String, val textKey: String, val intents: List<IntentSpec>)

/** Reads and queries `assets/oem_guides.json`, the editable table of manufacturer guides. Pure, so it is unit tested. */
object OemGuides {
    const val ASSET = "oem_guides.json"
    const val GENERIC_ID = "generic"
    private const val SUPPORTED_SCHEMA = 1

    private val json = Json { ignoreUnknownKeys = true }

    /** @return null when the text is not a guide file this version understands; callers then use [fallback]. */
    fun parse(text: String): OemGuideFile? = try {
        json.decodeFromString(OemGuideFile.serializer(), text).takeIf { it.schema == SUPPORTED_SCHEMA && it.guides.isNotEmpty() }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** The guide whose manufacturer list contains [manufacturer] or [brand] (case-insensitive); the generic guide otherwise. */
    fun select(file: OemGuideFile, manufacturer: String?, brand: String?): OemGuide {
        val names = listOfNotNull(manufacturer, brand).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val match = file.guides.firstOrNull { guide -> guide.manufacturers.any { it.lowercase() in names } }
        return match ?: genericOf(file)
    }

    fun genericOf(file: OemGuideFile): OemGuide = file.guides.firstOrNull { it.id == GENERIC_ID } ?: file.guides.firstOrNull() ?: fallback().guides.first()

    /**
     * The topics of [guide] in the order of [GuideTopics.all]. A topic the guide does not define comes from the generic guide;
     * the generic guide's screens are always appended as the last resort.
     */
    fun resolve(file: OemGuideFile, guide: OemGuide): List<ResolvedTopic> {
        val generic = genericOf(file)
        return GuideTopics.all.mapNotNull { id ->
            val own = guide.topics[id]
            val fallback = generic.topics[id]
            val primary = own ?: fallback ?: return@mapNotNull null
            val intents = (own?.intents.orEmpty() + fallback?.intents.orEmpty()).filter { it.isUsable }.distinct()
            ResolvedTopic(id, primary.textKey, intents)
        }
    }

    /** Used when the asset is missing or damaged: one generic guide that only opens the app's own settings page. */
    fun fallback(): OemGuideFile {
        val details = IntentSpec(action = "android.settings.APPLICATION_DETAILS_SETTINGS", data = "package:{package}")
        return OemGuideFile(
            schema = SUPPORTED_SCHEMA,
            guides = listOf(
                OemGuide(
                    id = GENERIC_ID,
                    topics = GuideTopics.all.associateWith { GuideTopic("onboarding_oem_generic_$it", listOf(details)) }
                )
            )
        )
    }
}
