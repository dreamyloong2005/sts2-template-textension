package com.dreamyloong.template.sts2

const val STS2_STEAM_BRANCH_PUBLIC = "public"
const val STS2_STEAM_BRANCH_PUBLIC_BETA = "public-beta"
const val STS2_STEAM_BRANCH_DEFAULT = STS2_STEAM_BRANCH_PUBLIC

enum class Sts2GameFileCheckStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    CANCELED,
    FAILED,
}

enum class Sts2GameFileCheckMode {
    STEAM,
    SIMPLE,
}

enum class Sts2SteamVerificationStatus {
    UNVERIFIED,
    PASSED,
    FAILED,
}

enum class Sts2GameFileDownloadStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELED,
    FAILED,
}

data class Sts2GameFilePreferences(
    val selectedSteamAccountSubjectId: String? = null,
    val steamVerificationEnabled: Boolean = true,
    val steamBranch: String = STS2_STEAM_BRANCH_DEFAULT,
)

data class Sts2SteamVerificationRecord(
    val status: Sts2SteamVerificationStatus = Sts2SteamVerificationStatus.UNVERIFIED,
    val versionClientId: Int? = null,
    val gameDirectory: String? = null,
    val steamBranch: String = STS2_STEAM_BRANCH_DEFAULT,
    val message: String = "",
    val checkedAtMillis: Long? = null,
) {
    val passed: Boolean
        get() = status == Sts2SteamVerificationStatus.PASSED

    fun matches(
        version: Sts2VersionDefinition?,
        steamBranch: String,
    ): Boolean {
        return versionClientId == version?.clientId &&
            gameDirectory == version?.gameDirectory?.trim() &&
            this.steamBranch == normalizeSteamBranch(steamBranch)
    }
}

data class Sts2SteamRepairPlanRecord(
    val versionClientId: Int? = null,
    val gameDirectory: String? = null,
    val steamBranch: String = STS2_STEAM_BRANCH_DEFAULT,
    val steamManifestId: String? = null,
    val steamManifestPath: String? = null,
    val steamDepotKeyHex: String? = null,
    val repairFilePaths: List<String> = emptyList(),
    val expectedFileCount: Int? = null,
    val localFileCount: Int? = null,
    val missingFileCount: Int? = null,
    val mismatchedFileCount: Int? = null,
    val checkedAtMillis: Long? = null,
) {
    val available: Boolean
        get() = versionClientId != null &&
            !steamManifestId.isNullOrBlank() &&
            !steamManifestPath.isNullOrBlank() &&
            !steamDepotKeyHex.isNullOrBlank() &&
            checkedAtMillis != null

    fun matches(
        version: Sts2VersionDefinition?,
        steamBranch: String,
        steamManifestId: String,
    ): Boolean {
        return versionClientId == version?.clientId &&
            gameDirectory == version?.gameDirectory?.trim() &&
            this.steamBranch == normalizeSteamBranch(steamBranch) &&
            this.steamManifestId == steamManifestId
    }
}

data class Sts2GameFileCheckSnapshot(
    val status: Sts2GameFileCheckStatus = Sts2GameFileCheckStatus.IDLE,
    val mode: Sts2GameFileCheckMode = Sts2GameFileCheckMode.STEAM,
    val versionClientId: Int? = null,
    val gameDirectory: String? = null,
    val steamAccountSubjectId: String? = null,
    val steamBranch: String = STS2_STEAM_BRANCH_DEFAULT,
    val passed: Boolean = false,
    val message: String = "",
    val targetPath: String? = null,
    val currentFilePath: String? = null,
    val expectedFileCount: Int? = null,
    val localFileCount: Int? = null,
    val checkedFileCount: Int? = null,
    val okFileCount: Int? = null,
    val missingFileCount: Int? = null,
    val mismatchedFileCount: Int? = null,
    val sizeOnlyFileCount: Int? = null,
    val extraFileCount: Int? = null,
    val problemFilesPreview: List<String> = emptyList(),
    val steamManifestId: String? = null,
    val steamManifestPath: String? = null,
    val steamDepotKeyHex: String? = null,
    val repairFilePaths: List<String> = emptyList(),
    val checkedAtMillis: Long? = null,
) {
    fun matches(
        version: Sts2VersionDefinition?,
        mode: Sts2GameFileCheckMode,
        steamBranch: String,
    ): Boolean {
        return versionClientId == version?.clientId &&
            gameDirectory == version?.gameDirectory?.trim() &&
            this.mode == mode &&
            this.steamBranch == normalizeSteamBranch(steamBranch)
    }
}

data class Sts2GameFileDownloadSnapshot(
    val status: Sts2GameFileDownloadStatus = Sts2GameFileDownloadStatus.IDLE,
    val versionClientId: Int? = null,
    val gameDirectory: String? = null,
    val steamAccountSubjectId: String? = null,
    val steamBranch: String = STS2_STEAM_BRANCH_DEFAULT,
    val message: String = "",
    val currentFilePath: String? = null,
    val totalFileCount: Int? = null,
    val completedFileCount: Int? = null,
    val skippedFileCount: Int? = null,
    val deletedExtraFileCount: Int? = null,
    val currentFileDownloadedBytes: Long? = null,
    val currentFileTotalBytes: Long? = null,
    val totalBytes: Long? = null,
    val downloadedBytes: Long? = null,
    val updatedAtMillis: Long? = null,
) {
    fun matches(
        version: Sts2VersionDefinition?,
        steamBranch: String,
    ): Boolean {
        return versionClientId == version?.clientId &&
            gameDirectory == version?.gameDirectory?.trim() &&
            this.steamBranch == normalizeSteamBranch(steamBranch)
    }
}

fun normalizeSteamBranch(branch: String): String {
    return when (branch.trim().lowercase()) {
        STS2_STEAM_BRANCH_PUBLIC_BETA -> STS2_STEAM_BRANCH_PUBLIC_BETA
        else -> STS2_STEAM_BRANCH_DEFAULT
    }
}

fun Sts2VersionDefinition.normalizedGameFileVersion(): Sts2VersionDefinition {
    return copy(
        versionName = versionName.trim(),
        gameDirectory = gameDirectory.trim(),
        saveDirectory = saveDirectory.trim(),
        modDirectory = modDirectory.trim(),
    )
}
