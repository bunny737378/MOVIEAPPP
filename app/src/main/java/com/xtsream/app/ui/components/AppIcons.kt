package com.xtsream.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight 24dp Material vector icons so the app does not bundle the 20MB+
 * material-icons-extended library in debug builds.
 */
object AppIcons {

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()
    }

    val Bookmark: ImageVector by lazy {
        icon("Bookmark") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(17f, 3f)
                horizontalLineTo(7f)
                curveTo(5.9f, 3f, 5f, 3.9f, 5f, 5f)
                verticalLineTo(21f)
                lineTo(12f, 18f)
                lineTo(19f, 21f)
                verticalLineTo(5f)
                curveTo(19f, 3.9f, 18.1f, 3f, 17f, 3f)
                close()
            }
        }
    }

    val BookmarkBorder: ImageVector by lazy {
        icon("BookmarkBorder") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(17f, 3f)
                horizontalLineTo(7f)
                curveTo(5.9f, 3f, 5f, 3.9f, 5f, 5f)
                verticalLineTo(21f)
                lineTo(12f, 18f)
                lineTo(19f, 21f)
                verticalLineTo(5f)
                curveTo(19f, 3.9f, 18.1f, 3f, 17f, 3f)
                close()
                moveTo(17f, 18f)
                lineTo(12f, 15.82f)
                lineTo(7f, 18f)
                verticalLineTo(5f)
                horizontalLineTo(17f)
                verticalLineTo(18f)
                close()
            }
        }
    }

    val Bookmarks: ImageVector by lazy {
        icon("Bookmarks") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 18f)
                lineTo(21f, 19f)
                verticalLineTo(3f)
                curveTo(21f, 1.9f, 20.1f, 1f, 19f, 1f)
                horizontalLineTo(8.99f)
                curveTo(7.89f, 1f, 7f, 1.9f, 7f, 3f)
                horizontalLineTo(17f)
                curveTo(18.1f, 3f, 19f, 3.9f, 19f, 5f)
                verticalLineTo(18f)
                close()
                moveTo(15f, 5f)
                horizontalLineTo(5f)
                curveTo(3.9f, 5f, 3f, 5.9f, 3f, 7f)
                verticalLineTo(23f)
                lineTo(10f, 20f)
                lineTo(17f, 23f)
                verticalLineTo(7f)
                curveTo(17f, 5.9f, 16.1f, 5f, 15f, 5f)
                close()
            }
        }
    }

    val BookmarksOutlined: ImageVector by lazy {
        icon("BookmarksOutlined") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15f, 7f)
                verticalLineTo(19.97f)
                lineTo(10.79f, 18.16f)
                lineTo(10f, 17.82f)
                lineTo(9.21f, 18.16f)
                lineTo(5f, 19.97f)
                verticalLineTo(7f)
                horizontalLineTo(15f)
                moveTo(19f, 1f)
                horizontalLineTo(8.99f)
                curveTo(7.89f, 1f, 7f, 1.9f, 7f, 3f)
                horizontalLineTo(17f)
                curveTo(18.1f, 3f, 19f, 3.9f, 19f, 5f)
                verticalLineTo(18f)
                lineTo(21f, 19f)
                verticalLineTo(3f)
                curveTo(21f, 1.9f, 20.1f, 1f, 19f, 1f)
                close()
                moveTo(15f, 5f)
                horizontalLineTo(5f)
                curveTo(3.9f, 5f, 3f, 5.9f, 3f, 7f)
                verticalLineTo(23f)
                lineTo(10f, 20f)
                lineTo(17f, 23f)
                verticalLineTo(7f)
                curveTo(17f, 5.9f, 16.1f, 5f, 15f, 5f)
                close()
            }
        }
    }

    val Extension: ImageVector by lazy {
        icon("Extension") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20.5f, 11f)
                horizontalLineTo(19f)
                verticalLineTo(7f)
                curveTo(19f, 5.9f, 18.1f, 5f, 17f, 5f)
                horizontalLineTo(13f)
                verticalLineTo(3.5f)
                curveTo(13f, 2.12f, 11.88f, 1f, 10.5f, 1f)
                reflectiveCurveTo(8f, 2.12f, 8f, 3.5f)
                verticalLineTo(5f)
                horizontalLineTo(4f)
                curveTo(2.9f, 5f, 2.01f, 5.9f, 2.01f, 7f)
                verticalLineTo(10.8f)
                horizontalLineTo(3.5f)
                curveTo(4.99f, 10.8f, 6.2f, 12.01f, 6.2f, 13.5f)
                reflectiveCurveTo(4.99f, 16.2f, 3.5f, 16.2f)
                horizontalLineTo(2f)
                verticalLineTo(20f)
                curveTo(2f, 21.1f, 2.9f, 22f, 4f, 22f)
                horizontalLineTo(7.8f)
                verticalLineTo(20.5f)
                curveTo(7.8f, 19.01f, 9.01f, 17.8f, 10.5f, 17.8f)
                reflectiveCurveTo(13.2f, 19.01f, 13.2f, 20.5f)
                verticalLineTo(22f)
                horizontalLineTo(17f)
                curveTo(18.1f, 22f, 19f, 21.1f, 19f, 20f)
                verticalLineTo(16f)
                horizontalLineTo(20.5f)
                curveTo(21.88f, 16f, 23f, 14.88f, 23f, 13.5f)
                reflectiveCurveTo(21.88f, 11f, 20.5f, 11f)
                close()
            }
        }
    }

    val Movie: ImageVector by lazy {
        icon("Movie") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(18f, 4f)
                lineTo(20f, 8f)
                horizontalLineTo(17f)
                lineTo(15f, 4f)
                horizontalLineTo(13f)
                lineTo(15f, 8f)
                horizontalLineTo(12f)
                lineTo(10f, 4f)
                horizontalLineTo(8f)
                lineTo(10f, 8f)
                horizontalLineTo(7f)
                lineTo(5f, 4f)
                horizontalLineTo(4f)
                curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
                lineTo(2f, 18f)
                curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
                horizontalLineTo(20f)
                curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
                verticalLineTo(4f)
                horizontalLineTo(18f)
                close()
            }
        }
    }

    val CloudOff: ImageVector by lazy {
        icon("CloudOff") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19.35f, 10.04f)
                curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
                curveTo(10.52f, 4f, 9.15f, 4.43f, 7.99f, 5.17f)
                lineTo(9.45f, 6.63f)
                curveTo(10.22f, 6.23f, 11.09f, 6f, 12f, 6f)
                curveTo(14.76f, 6f, 17f, 8.24f, 17f, 11f)
                verticalLineTo(12f)
                horizontalLineTo(19f)
                curveTo(20.66f, 12f, 22f, 13.34f, 22f, 15f)
                curveTo(22f, 16.13f, 21.37f, 17.11f, 20.44f, 17.62f)
                lineTo(21.89f, 19.07f)
                curveTo(23.16f, 18.16f, 24f, 16.68f, 24f, 15f)
                curveTo(24f, 12.36f, 21.95f, 10.22f, 19.35f, 10.04f)
                close()
                moveTo(3f, 5.27f)
                lineTo(5.75f, 8.01f)
                curveTo(2.56f, 8.15f, 0f, 10.77f, 0f, 14f)
                curveTo(0f, 17.31f, 2.69f, 20f, 6f, 20f)
                horizontalLineTo(17.73f)
                lineTo(19.73f, 22f)
                lineTo(21f, 20.73f)
                lineTo(4.27f, 4f)
                lineTo(3f, 5.27f)
                close()
            }
        }
    }

    val GridView: ImageVector by lazy {
        icon("GridView") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 3f)
                verticalLineTo(11f)
                horizontalLineTo(11f)
                verticalLineTo(3f)
                horizontalLineTo(3f)
                close()
                moveTo(9f, 9f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                horizontalLineTo(9f)
                verticalLineTo(9f)
                close()
                moveTo(3f, 13f)
                verticalLineTo(21f)
                horizontalLineTo(11f)
                verticalLineTo(13f)
                horizontalLineTo(3f)
                close()
                moveTo(9f, 19f)
                horizontalLineTo(5f)
                verticalLineTo(15f)
                horizontalLineTo(9f)
                verticalLineTo(19f)
                close()
                moveTo(13f, 3f)
                verticalLineTo(11f)
                horizontalLineTo(21f)
                verticalLineTo(3f)
                horizontalLineTo(13f)
                close()
                moveTo(19f, 9f)
                horizontalLineTo(15f)
                verticalLineTo(5f)
                horizontalLineTo(19f)
                verticalLineTo(9f)
                close()
                moveTo(13f, 13f)
                verticalLineTo(21f)
                horizontalLineTo(21f)
                verticalLineTo(13f)
                horizontalLineTo(13f)
                close()
                moveTo(19f, 19f)
                horizontalLineTo(15f)
                verticalLineTo(15f)
                horizontalLineTo(19f)
                verticalLineTo(19f)
                close()
            }
        }
    }

    val ViewList: ImageVector by lazy {
        icon("ViewList") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 14f)
                horizontalLineTo(7f)
                verticalLineTo(10f)
                horizontalLineTo(3f)
                verticalLineTo(14f)
                close()
                moveTo(3f, 19f)
                horizontalLineTo(7f)
                verticalLineTo(15f)
                horizontalLineTo(3f)
                verticalLineTo(19f)
                close()
                moveTo(3f, 9f)
                horizontalLineTo(7f)
                verticalLineTo(5f)
                horizontalLineTo(3f)
                verticalLineTo(9f)
                close()
                moveTo(8f, 14f)
                horizontalLineTo(21f)
                verticalLineTo(10f)
                horizontalLineTo(8f)
                verticalLineTo(14f)
                close()
                moveTo(8f, 19f)
                horizontalLineTo(21f)
                verticalLineTo(15f)
                horizontalLineTo(8f)
                verticalLineTo(19f)
                close()
                moveTo(8f, 5f)
                verticalLineTo(9f)
                horizontalLineTo(21f)
                verticalLineTo(5f)
                horizontalLineTo(8f)
                close()
            }
        }
    }

    val ViewAgenda: ImageVector by lazy {
        icon("ViewAgenda") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 13f)
                horizontalLineTo(5f)
                curveTo(3.9f, 13f, 3f, 13.9f, 3f, 15f)
                verticalLineTo(19f)
                curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
                horizontalLineTo(19f)
                curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                verticalLineTo(15f)
                curveTo(21f, 13.9f, 20.1f, 13f, 19f, 13f)
                close()
                moveTo(19f, 3f)
                horizontalLineTo(5f)
                curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
                verticalLineTo(9f)
                curveTo(3f, 10.1f, 3.9f, 11f, 5f, 11f)
                horizontalLineTo(19f)
                curveTo(20.1f, 11f, 21f, 10.1f, 21f, 9f)
                verticalLineTo(5f)
                curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
                close()
            }
        }
    }

    val History: ImageVector by lazy {
        icon("History") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13f, 3f)
                curveTo(8.03f, 3f, 4f, 7.03f, 4f, 12f)
                horizontalLineTo(1f)
                lineTo(4.89f, 15.89f)
                lineTo(4.96f, 16.03f)
                lineTo(9f, 12f)
                horizontalLineTo(6f)
                curveTo(6f, 8.13f, 9.13f, 5f, 13f, 5f)
                reflectiveCurveTo(20f, 8.13f, 20f, 12f)
                reflectiveCurveTo(16.87f, 19f, 13f, 19f)
                curveTo(11.07f, 19f, 9.32f, 18.21f, 8.06f, 16.94f)
                lineTo(6.64f, 18.36f)
                curveTo(8.27f, 19.99f, 10.51f, 21f, 13f, 21f)
                curveTo(17.97f, 21f, 22f, 16.97f, 22f, 12f)
                reflectiveCurveTo(17.97f, 3f, 13f, 3f)
                close()
                moveTo(12f, 8f)
                verticalLineTo(13f)
                lineTo(16.28f, 15.54f)
                lineTo(17f, 14.33f)
                lineTo(13.5f, 12.25f)
                verticalLineTo(8f)
                horizontalLineTo(12f)
                close()
            }
        }
    }

    val Download: ImageVector by lazy {
        icon("Download") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 20f)
                horizontalLineTo(19f)
                verticalLineTo(18f)
                horizontalLineTo(5f)
                verticalLineTo(20f)
                close()
                moveTo(19f, 9f)
                horizontalLineTo(15f)
                verticalLineTo(3f)
                horizontalLineTo(9f)
                verticalLineTo(9f)
                horizontalLineTo(5f)
                lineTo(12f, 16f)
                lineTo(19f, 9f)
                close()
            }
        }
    }

    val Link: ImageVector by lazy {
        icon("Link") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3.9f, 12f)
                curveTo(3.9f, 10.29f, 5.29f, 8.9f, 7f, 8.9f)
                horizontalLineTo(11f)
                verticalLineTo(7f)
                horizontalLineTo(7f)
                curveTo(4.24f, 7f, 2f, 9.24f, 2f, 12f)
                reflectiveCurveTo(4.24f, 17f, 7f, 17f)
                horizontalLineTo(11f)
                verticalLineTo(15.1f)
                horizontalLineTo(7f)
                curveTo(5.29f, 15.1f, 3.9f, 13.71f, 3.9f, 12f)
                close()
                moveTo(8f, 13f)
                horizontalLineTo(16f)
                verticalLineTo(11f)
                horizontalLineTo(8f)
                verticalLineTo(13f)
                close()
                moveTo(17f, 7f)
                horizontalLineTo(13f)
                verticalLineTo(8.9f)
                horizontalLineTo(17f)
                curveTo(18.71f, 8.9f, 20.1f, 10.29f, 20.1f, 12f)
                reflectiveCurveTo(18.71f, 15.1f, 17f, 15.1f)
                horizontalLineTo(13f)
                verticalLineTo(17f)
                horizontalLineTo(17f)
                curveTo(19.76f, 17f, 22f, 14.76f, 22f, 12f)
                reflectiveCurveTo(19.76f, 7f, 17f, 7f)
                close()
            }
        }
    }

    val FolderOpen: ImageVector by lazy {
        icon("FolderOpen") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 6f)
                horizontalLineTo(12f)
                lineTo(10f, 4f)
                horizontalLineTo(4f)
                curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
                lineTo(2f, 18f)
                curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
                horizontalLineTo(20f)
                curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
                verticalLineTo(8f)
                curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f)
                close()
                moveTo(20f, 18f)
                horizontalLineTo(4f)
                verticalLineTo(8f)
                horizontalLineTo(20f)
                verticalLineTo(18f)
                close()
            }
        }
    }

    val Dns: ImageVector by lazy {
        icon("Dns") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 13f)
                horizontalLineTo(4f)
                curveTo(3.45f, 13f, 3f, 13.45f, 3f, 14f)
                verticalLineTo(20f)
                curveTo(3f, 20.55f, 3.45f, 21f, 4f, 21f)
                horizontalLineTo(20f)
                curveTo(20.55f, 21f, 21f, 20.55f, 21f, 20f)
                verticalLineTo(14f)
                curveTo(21f, 13.45f, 20.55f, 13f, 20f, 13f)
                close()
                moveTo(7f, 19f)
                curveTo(5.9f, 19f, 5f, 18.1f, 5f, 17f)
                reflectiveCurveTo(5.9f, 15f, 7f, 15f)
                reflectiveCurveTo(9f, 15.9f, 9f, 17f)
                reflectiveCurveTo(8.1f, 19f, 7f, 19f)
                close()
                moveTo(20f, 3f)
                horizontalLineTo(4f)
                curveTo(3.45f, 3f, 3f, 3.45f, 3f, 4f)
                verticalLineTo(10f)
                curveTo(3f, 10.55f, 3.45f, 11f, 4f, 11f)
                horizontalLineTo(20f)
                curveTo(20.55f, 11f, 21f, 10.55f, 21f, 10f)
                verticalLineTo(4f)
                curveTo(21f, 3.45f, 20.55f, 3f, 20f, 3f)
                close()
                moveTo(7f, 9f)
                curveTo(5.9f, 9f, 5f, 8.1f, 5f, 7f)
                reflectiveCurveTo(5.9f, 5f, 7f, 5f)
                reflectiveCurveTo(9f, 5.9f, 9f, 7f)
                reflectiveCurveTo(8.1f, 9f, 7f, 9f)
                close()
            }
        }
    }

    val Schedule: ImageVector by lazy {
        icon("Schedule") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11.99f, 2f)
                curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveTo(6.47f, 22f, 11.99f, 22f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
                close()
                moveTo(12f, 20f)
                curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
                reflectiveCurveTo(7.58f, 4f, 12f, 4f)
                reflectiveCurveTo(20f, 7.58f, 20f, 12f)
                reflectiveCurveTo(16.42f, 20f, 12f, 20f)
                close()
                moveTo(12.5f, 7f)
                horizontalLineTo(11f)
                verticalLineTo(13f)
                lineTo(16.25f, 16.15f)
                lineTo(17f, 14.92f)
                lineTo(12.5f, 12.25f)
                verticalLineTo(7f)
                close()
            }
        }
    }

    val CalendarToday: ImageVector by lazy {
        icon("CalendarToday") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 3f)
                horizontalLineTo(19f)
                verticalLineTo(1f)
                horizontalLineTo(17f)
                verticalLineTo(3f)
                horizontalLineTo(7f)
                verticalLineTo(1f)
                horizontalLineTo(5f)
                verticalLineTo(3f)
                horizontalLineTo(4f)
                curveTo(2.9f, 3f, 2f, 3.9f, 2f, 5f)
                verticalLineTo(21f)
                curveTo(2f, 22.1f, 2.9f, 23f, 4f, 23f)
                horizontalLineTo(20f)
                curveTo(21.1f, 23f, 22f, 22.1f, 22f, 21f)
                verticalLineTo(5f)
                curveTo(22f, 3.9f, 21.1f, 3f, 20f, 3f)
                close()
                moveTo(20f, 21f)
                horizontalLineTo(4f)
                verticalLineTo(8f)
                horizontalLineTo(20f)
                verticalLineTo(21f)
                close()
            }
        }
    }

    val SwapVert: ImageVector by lazy {
        icon("SwapVert") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 17.01f)
                verticalLineTo(10f)
                horizontalLineTo(14f)
                verticalLineTo(17.01f)
                horizontalLineTo(11f)
                lineTo(15f, 21f)
                lineTo(19f, 17.01f)
                horizontalLineTo(16f)
                close()
                moveTo(9f, 3f)
                lineTo(5f, 6.99f)
                horizontalLineTo(8f)
                verticalLineTo(14f)
                horizontalLineTo(10f)
                verticalLineTo(6.99f)
                horizontalLineTo(13f)
                lineTo(9f, 3f)
                close()
            }
        }
    }
}
