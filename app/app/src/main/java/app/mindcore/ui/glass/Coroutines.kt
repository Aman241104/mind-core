/*
 * Adapted from Kyant0/AndroidLiquidGlass catalog (utils/Coroutines.kt, common + android)
 * https://github.com/Kyant0/AndroidLiquidGlass, Copyright Kyant0, Apache License 2.0
 * Changes: package renamed, expect/actual merged for an Android-only app.
 */
package app.mindcore.ui.glass

import kotlinx.coroutines.android.awaitFrame as androidAwaitFrame

suspend fun awaitFrame() {
    androidAwaitFrame()
}
