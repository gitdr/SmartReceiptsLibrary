package co.smartreceipts.android.tooltip.model

import android.support.annotation.StringRes

/**
 * Models the required metadata to display a specific tooltip to our end users
 */
interface TooltipMetadata {

    /**
     * @return the [TooltipType] to display
     */
    val type: TooltipType

    /**
     * @return the priority. Higher priorities outweigh lower ones (i.e. higher ones will be
     * displayed before lower ones)
     */
    val priority: Int

    /**
     * @return the [StringRes] for the tooltip message
     */
    val messageResourceId: Int

    /**
     * @return if we should show a warning icon (ie '!') before the message
     */
    val showWarningIcon: Boolean

    /**
     * @return if the 'X' icon should appear to allow the user to close the tooltip
     */
    val showCloseIcon: Boolean

    /**
     * While this is similar to the [showCloseIcon], this may be more relevant in certain contexts,
     * where we need to explicitly show the user text instead of an icon. Instances may only have
     * this or [showCloseIcon] as true but not both.
     *
     * @return if an explicit 'Cancel' button should appear.
     */
    val showCancelButton: Boolean
}