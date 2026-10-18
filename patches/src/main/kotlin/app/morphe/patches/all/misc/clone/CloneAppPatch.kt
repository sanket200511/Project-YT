/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * Original code hard forked from:
 * https://github.com/ReVanced/revanced-patches/blob/724e6d61b2ecd868c1a9a37d465a688e83a74799/patches/src/main/kotlin/app/revanced/patches/all/misc/packagename/ChangePackageNamePatch.kt
 *
 * File-Specific License Notice (GPLv3 Section 7 Terms)
 *
 * This file is part of the Morphe project and is licensed under
 * the GNU General Public License version 3 (GPLv3), with the Additional
 * Terms under Section 7 described in the LICENSE file.
 *
 * https://www.gnu.org/licenses/gpl-3.0.html
 *
 * Section 7b: Notice Preservation
 * -------------------------------
 * This entire comment block must be preserved in all copies,
 * distributions, and derivative works of this file, in both
 * original and modified source forms.
 *
 * Portions of this software are provided "AS IS" by the Morphe software project.
 * Any express or implied warranties, including the implied warranties of
 * merchantability and fitness for a particular purpose, are disclaimed.
 */

package app.morphe.patches.all.misc.clone

import app.morphe.patcher.patch.Option
import app.morphe.patcher.patch.OptionException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.util.asSequence
import app.morphe.util.findElementByAttributeValue
import org.w3c.dom.Element
import java.util.logging.Logger

private lateinit var packageNameOption: Option<String>

/**
 * Set the package name to use.
 * If this is called multiple times, the first call will set the package name.
 *
 * @param fallbackPackageName The package name to use if the user has not already specified a package name.
 * @return The package name that was set.
 * @throws OptionException.ValueValidationException If the package name is invalid.
 */
fun setOrGetFallbackPackageName(fallbackPackageName: String): String {
    val packageName = packageNameOption.value!!

    return if (packageName == packageNameOption.default) {
        fallbackPackageName.also { packageNameOption.value = it }
    } else {
        packageName
    }
}

@Suppress("unused")
val cloneAppPatch = resourcePatch(
    name = "Clone app",
    description = "Changes the app package name to allow installing the same app multiple times. By default \".vista\" is appended to the package name. Each cloned install must use a unique package name. Cloning does not work with all apps and using this patch may cause app crashes or other unexpected behavior."
) {
    packageNameOption = stringOption(
        key = "packageName",
        title = "Package name",
        description = "The package name to use for the app.",
        default = ""
    )

    val updatePermissionsOption by booleanOption(
        key = "updatePermissions",
        title = "Update permissions",
        description = "Update permissions to match the new package name.",
        default = true
    )

    val updateProvidersOption by booleanOption(
        key = "updateProviders",
        title = "Update providers",
        description = "Update providers to match the new package name.",
        default = true
    )

    fun getReplacementPackageName(originalPackageName: String): String {
        val replacementPackageName = packageNameOption.value
        return if (!replacementPackageName.isNullOrEmpty() && replacementPackageName != packageNameOption.default) {
            replacementPackageName
        } else {
            "$originalPackageName.vista"
        }
    }

    finalize {
        /**
         * Apps that are confirmed to not work correctly with this patch.
         * This is not an exhaustive list, and is only the apps with
         * Morphe specific patches and are confirmed incompatible with this patch.
         */
        val incompatibleAppPackages = setOf(
            // Crashes when opening posts
            "com.twitter.android",

            // Video stories only play audios
            "com.instagram.android",

            // Patches and installs but crashes on launch.
            "com.duolingo",
            "tv.twitch.android.app",

            // Resources fail to decode correctly.
            // https://github.com/MorpheApp/morphe-patcher/issues/178
            "cn.wps.moffice_eng",
        )

        val packageName = packageMetadata.packageName
        val newPackageName = getReplacementPackageName(packageName)

        if (incompatibleAppPackages.contains(packageName)) {
            return@finalize Logger.getLogger(this::class.java.name).severe(
                "'$packageName' does not work correctly with \"Clone app\", and no changes were made.",
            )
        }

        val applyUpdatePermissions : Boolean
        val applyUpdateProviders : Boolean

        // Override user options with known working values for specific apps.
        when (packageName) {
            "com.google.android.youtube", "com.google.android.apps.youtube.music",
            "com.google.android.apps.photos", "com.google.android.apps.magazines" -> {
                // Permissions and providers are updated by "GmsCore support" patch
                applyUpdatePermissions = false
                applyUpdateProviders = false
            }
            else -> {
                applyUpdatePermissions = updatePermissionsOption.value!!
                applyUpdateProviders = updateProvidersOption.value!!
            }
        }

        val providerStringResources = mutableSetOf<String>()

        document("AndroidManifest.xml").use { document ->
            document.documentElement.setAttribute("package", newPackageName)

            if (applyUpdatePermissions) {
                val permissions = document.getElementsByTagName("permission")
                val usesPermissions = document.getElementsByTagName("uses-permission")

                permissions.asSequence().map { it as Element }.forEach {
                    val oldName = it.getAttribute("android:name")
                    val newName = when {
                        oldName.startsWith('.') -> return@forEach
                        oldName.startsWith("$packageName.") -> oldName.replaceFirst(packageName, newPackageName)
                        else -> "${newPackageName}_$oldName"
                    }
                    it.setAttribute("android:name", newName)

                    // Update a corresponding <uses-permission> as well if it exists
                    usesPermissions
                        .findElementByAttributeValue("android:name", oldName)
                        ?.setAttribute("android:name", newName)
                }
            }

            if (applyUpdateProviders) {
                val providers =
                    document.getElementsByTagName("provider").asSequence().map { it as Element }

                for (provider in providers) {
                    val authorities = provider.getAttribute("android:authorities").split(';')
                    val newAuthorities = authorities.map {
                        when {
                            it.startsWith("$packageName.") -> it.replaceFirst(packageName, newPackageName)
                            it.startsWith('@') -> {
                                providerStringResources.add(it.removePrefix("@string/"))
                                it
                            }
                            else -> "${newPackageName}_$it"
                        }
                    }
                    provider.setAttribute("android:authorities", newAuthorities.joinToString(";"))
                }
            }
        }

        if (providerStringResources.isNotEmpty()) {
            document("res/values/strings.xml").use { document ->
                val children = document.documentElement.childNodes
                for (i in 0 until children.length) {
                    val node = children.item(i) as? Element ?: continue

                    if (node.getAttribute("name") in providerStringResources) {
                        val authority = node.textContent
                        node.textContent = if (authority.startsWith("$packageName.")) {
                            authority.replaceFirst(packageName, newPackageName)
                        } else {
                            "${newPackageName}_$authority"
                        }
                    }
                }
            }
        }
    }
}
