package com.dreamyloong.template.sts2

import com.dreamyloong.tlauncher.sdk.extension.ExtensionPackageResources
import com.dreamyloong.tlauncher.sdk.i18n.LocalizedText
import com.dreamyloong.tlauncher.sdk.i18n.SupportedLanguage
import org.json.JSONObject

data class Sts2TemplateStrings(
    val name: LocalizedText = STS2_TEMPLATE_NAME,
    val defaultInstanceDescription: LocalizedText = STS2_TEMPLATE_DEFAULT_INSTANCE_DESCRIPTION,
    val targetDescription: LocalizedText = STS2_TEMPLATE_TARGET_DESCRIPTION,
    val targetNotes: LocalizedText = STS2_TEMPLATE_TARGET_NOTES,
    val capabilityLabels: Map<String, LocalizedText> = STS2_TEMPLATE_CAPABILITY_LABELS,
)

enum class Sts2TemplateLocalizationTarget(
    val resourceDirectory: String,
    val descriptionKey: String,
    val notesKey: String,
) {
    ANDROID(
        resourceDirectory = "android",
        descriptionKey = "template.android.description",
        notesKey = "template.android.notes",
    ),
    WINDOWS(
        resourceDirectory = "windows",
        descriptionKey = "template.windows.description",
        notesKey = "template.windows.notes",
    ),
}

val STS2_TEMPLATE_NAME: LocalizedText = LocalizedText(
    zhCn = "杀戮尖塔2",
    enUs = "Slay the Spire 2",
)

val STS2_TEMPLATE_DEFAULT_INSTANCE_DESCRIPTION: LocalizedText = LocalizedText(
    zhCn = "启程去屠戮这座高塔。",
    enUs = "Set forth to slaughter the Spire.",
)

val STS2_TEMPLATE_TARGET_DESCRIPTION: LocalizedText = LocalizedText(
    zhCn = "官方 STS2 模板。",
    enUs = "Official STS2 template.",
)

val STS2_TEMPLATE_TARGET_NOTES: LocalizedText = LocalizedText(
    zhCn = "平台说明会从模板本地化资源中加载。",
    enUs = "Platform notes are loaded from template localization resources.",
)

val STS2_TEMPLATE_CAPABILITY_LABELS: Map<String, LocalizedText> = mapOf(
    "download" to LocalizedText(zhCn = "下载", enUs = "Download"),
    "patch" to LocalizedText(zhCn = "补丁", enUs = "Patch"),
    "launch" to LocalizedText(zhCn = "启动", enUs = "Launch"),
    "save" to LocalizedText(zhCn = "存档", enUs = "Save"),
    "browse" to LocalizedText(zhCn = "浏览", enUs = "Browse"),
    "version_manage" to LocalizedText(zhCn = "版本管理", enUs = "Version Manager"),
)

fun loadSts2TemplateStrings(
    resources: ExtensionPackageResources,
    target: Sts2TemplateLocalizationTarget,
): Sts2TemplateStrings {
    val zhCn = resources.readSts2LocalizationFiles(
        STS2_COMMON_ZH_CN_LOCALIZATION_PATH,
        target.localizationPath("zh_cn.json"),
    )
    val enUs = resources.readSts2LocalizationFiles(
        STS2_COMMON_EN_US_LOCALIZATION_PATH,
        target.localizationPath("en_us.json"),
    )
    installSts2LocalizationEntries(zhCn, enUs)
    return Sts2TemplateStrings(
        name = localizedText("template.name", zhCn, enUs, STS2_TEMPLATE_NAME),
        defaultInstanceDescription = localizedText(
            key = "template.defaultInstanceDescription",
            zhCn = zhCn,
            enUs = enUs,
            fallback = STS2_TEMPLATE_DEFAULT_INSTANCE_DESCRIPTION,
        ),
        targetDescription = localizedText(target.descriptionKey, zhCn, enUs, STS2_TEMPLATE_TARGET_DESCRIPTION),
        targetNotes = localizedText(target.notesKey, zhCn, enUs, STS2_TEMPLATE_TARGET_NOTES),
        capabilityLabels = STS2_TEMPLATE_CAPABILITY_LABELS.keys.associateWith { key ->
            localizedText("template.capability.$key", zhCn, enUs, STS2_TEMPLATE_CAPABILITY_LABELS.getValue(key))
        },
    )
}

fun sts2Localized(
    language: SupportedLanguage,
    key: String,
): String = sts2Localized(language, key, emptyList())

fun sts2Localized(
    language: SupportedLanguage,
    key: String,
    args: List<Any?>,
): String {
    val template = sts2LocalizationEntries[language]?.get(key)
        ?: sts2LocalizationEntries[SupportedLanguage.EN_US]?.get(key)
        ?: key
    return template.formatSts2LocalizationArgs(args)
}

private const val STS2_COMMON_ZH_CN_LOCALIZATION_PATH = "i18n/common/zh_cn.json"
private const val STS2_COMMON_EN_US_LOCALIZATION_PATH = "i18n/common/en_us.json"

@Volatile
private var sts2LocalizationEntries: Map<SupportedLanguage, Map<String, String>> = mapOf(
    SupportedLanguage.ZH_CN to emptyMap(),
    SupportedLanguage.EN_US to emptyMap(),
)

private fun installSts2LocalizationEntries(
    zhCn: Map<String, String>,
    enUs: Map<String, String>,
) {
    sts2LocalizationEntries = mapOf(
        SupportedLanguage.ZH_CN to zhCn,
        SupportedLanguage.EN_US to enUs,
    )
}

private fun localizedText(
    key: String,
    zhCn: Map<String, String>,
    enUs: Map<String, String>,
    fallback: LocalizedText,
): LocalizedText {
    return LocalizedText(
        zhCn = zhCn[key] ?: fallback.zhCn,
        enUs = enUs[key] ?: fallback.enUs,
    )
}

private fun Sts2TemplateLocalizationTarget.localizationPath(fileName: String): String {
    return "i18n/$resourceDirectory/$fileName"
}

private fun ExtensionPackageResources.readSts2LocalizationFiles(
    commonPath: String,
    platformPath: String,
): Map<String, String> {
    return readSts2LocalizationFile(commonPath) + readSts2LocalizationFile(platformPath)
}

private fun ExtensionPackageResources.readSts2LocalizationFile(path: String): Map<String, String> {
    val text = readUtf8(path) ?: return emptyMap()
    return runCatching {
        val json = JSONObject(text)
        val entries = linkedMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next().orEmpty()
            val value = json.opt(key)?.toString().orEmpty()
            if (key.isNotBlank() && value.isNotBlank()) {
                entries[key] = value
            }
        }
        entries.toMap()
    }.getOrDefault(emptyMap())
}

private fun String.formatSts2LocalizationArgs(args: List<Any?>): String {
    if (args.isEmpty()) {
        return this
    }
    var resolved = this
    args.forEachIndexed { index, value ->
        resolved = resolved.replace("{$index}", value?.toString().orEmpty())
    }
    return resolved
}
