// Tells the user how to fix a missing credential manager on their particular desktop.
package com.emberr.core.security.secrets

object CredentialManagerAdvice {

    fun remedyForCurrentDesktop(): String {
        if (isRunningOnKdePlasma()) return KDE_REMEDY
        if (isRunningOnLinux()) return GENERIC_LINUX_REMEDY
        return UNEXPECTED_PLATFORM_REMEDY
    }

    private fun isRunningOnKdePlasma(): Boolean {
        val desktopName = System.getenv("XDG_CURRENT_DESKTOP").orEmpty().lowercase()
        return desktopName.contains("kde") || desktopName.contains("plasma")
    }

    private fun isRunningOnLinux(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("linux")

    private const val KDE_REMEDY =
        "The simplest fix is to install oo7-daemon, which registers the Secret Service " +
            "interface itself on any desktop. Otherwise, KWallet does not register it automatically. " +
            "Create ~/.local/share/dbus-1/services/org.freedesktop.secrets.service containing:\n\n" +
            "[D-BUS Service]\n" +
            "Name=org.freedesktop.secrets\n" +
            "Exec=/usr/bin/kwalletd6\n\n" +
            "Then sign out and back in."

    private const val GENERIC_LINUX_REMEDY =
        "Install and start a credential manager, then restart Emberr. " +
            "Newer distributions ship oo7-daemon, older ones use gnome-keyring. " +
            "KeePassXC also works once you enable Secret Service integration in its settings."

    private const val UNEXPECTED_PLATFORM_REMEDY =
        "Emberr could not reach the system credential store. Restart Emberr, " +
            "and check that your user account is allowed to access it."
}
