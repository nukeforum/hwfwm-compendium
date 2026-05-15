package wizardry.compendium.drive.backup

interface BackupScheduler {
    fun schedulePeriodic()
    fun cancelPeriodic()
}
