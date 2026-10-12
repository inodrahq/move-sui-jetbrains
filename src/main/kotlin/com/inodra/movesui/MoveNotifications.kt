package com.inodra.movesui

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object MoveNotifications {
    const val GROUP = "Move for Sui"

    fun info(project: Project?, title: String, content: String, vararg actions: NotificationAction) =
        notify(project, title, content, NotificationType.INFORMATION, *actions)

    fun warn(project: Project?, title: String, content: String, vararg actions: NotificationAction) =
        notify(project, title, content, NotificationType.WARNING, *actions)

    fun error(project: Project?, title: String, content: String, vararg actions: NotificationAction) =
        notify(project, title, content, NotificationType.ERROR, *actions)

    private fun notify(project: Project?, title: String, content: String, type: NotificationType, vararg actions: NotificationAction): Notification {
        val n = NotificationGroupManager.getInstance().getNotificationGroup(GROUP).createNotification(title, content, type)
        actions.forEach { n.addAction(it) }
        n.notify(project)
        return n
    }
}
