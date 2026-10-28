package com.kyled.receiptscanner.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kyled.receiptscanner.ReceiptApp
import com.kyled.receiptscanner.ui.screens.CaptureScreen
import com.kyled.receiptscanner.ui.screens.ReceiptDetailScreen
import com.kyled.receiptscanner.ui.screens.ReceiptListScreen
import com.kyled.receiptscanner.ui.screens.RecentPhotosScreen
import com.kyled.receiptscanner.ui.screens.ReviewScreen

private object Routes {
    const val LIST = "list"
    const val CAPTURE = "capture"
    const val RECENT = "recent"
    const val DETAIL = "detail/{id}"
    const val REVIEW = "review?file={file}&path={path}&id={id}"

    fun detail(id: String) = "detail/${Uri.encode(id)}"
    fun reviewCapture(fileName: String) = "review?file=${Uri.encode(fileName)}"
    fun reviewStored(path: String) = "review?path=${Uri.encode(path)}"
    fun reviewEdit(id: String) = "review?id=${Uri.encode(id)}"
}

@Composable
fun AppNavigation(twoPane: Boolean, nav: NavHostController = rememberNavController()) {
    val repo = (LocalContext.current.applicationContext as ReceiptApp).container.receiptRepository

    NavHost(navController = nav, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            ReceiptListScreen(
                twoPane = twoPane,
                onScan = { nav.navigate(Routes.CAPTURE) },
                onOpen = { nav.navigate(Routes.detail(it)) },
                onEdit = { nav.navigate(Routes.reviewEdit(it)) },
                onRecentPhotos = { nav.navigate(Routes.RECENT) },
            )
        }

        composable(Routes.CAPTURE) {
            CaptureScreen(
                capturesDir = repo.capturesDir,
                onCaptured = { file ->
                    nav.navigate(Routes.reviewCapture(file.name)) {
                        // Back from review goes to the list, not to the camera.
                        popUpTo(Routes.CAPTURE) { inclusive = true }
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            Routes.REVIEW,
            arguments = listOf("file", "path", "id").map { name ->
                navArgument(name) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            },
        ) {
            ReviewScreen(
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack(Routes.LIST, inclusive = false) },
            )
        }

        composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ReceiptDetailScreen(
                receiptId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.reviewEdit(it)) },
                onDeleted = { nav.popBackStack() },
            )
        }

        composable(Routes.RECENT) {
            RecentPhotosScreen(
                onBack = { nav.popBackStack() },
                onPick = { path ->
                    nav.navigate(Routes.reviewStored(path)) {
                        popUpTo(Routes.RECENT) { inclusive = true }
                    }
                },
            )
        }
    }
}
