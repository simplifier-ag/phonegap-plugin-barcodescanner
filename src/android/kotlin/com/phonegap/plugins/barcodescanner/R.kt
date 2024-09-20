package com.phonegap.plugins.barcodescanner

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources

object R {
	/**
	 * @param filename     Name of the file
	 * @param resourceType Type of resource (ID, STRING, LAYOUT, DRAWABLE)
	 * @return The associated resource identifier. Returns 0 if no such resource was found. (0 is not a valid resource ID.)
	 */
	private fun getResourceId(context: Context, filename: String, resourceType: String): Int {
		val packageName = context.packageName
		val resources = context.resources

		return resources.getIdentifier(filename, resourceType, packageName)
	}

	/**
	 * @param identifier identifier of the drawable
	 * @return Id of the drawable. Returns 0 if no such resource was found.
	 */
	fun getDrawableId(context: Context, identifier: String): Int {
		return getResourceId(context, identifier, ResourceTypes.DRAWABLE)
	}

	/**
	 * @param context    activity context
	 * @param identifier drawable string_id
	 * @return a drawable from resources
	 */
	fun getDrawable(context: Context, identifier: String): Drawable? {
		return AppCompatResources.getDrawable(context, getDrawableId(context, identifier))
	}

	/**
	 * @param identifier identifier of the layout
	 * @return Id of the layout. Returns 0 if no such resource was found.
	 */
	fun getLayoutId(context: Context, identifier: String): Int {
		return getResourceId(context, identifier, ResourceTypes.LAYOUT)
	}

	/**
	 * Returns the Id of a view
	 *
	 * @param identifier identifier of the id
	 * @return Id of the view. Returns 0 if no such resource was found.
	 */
	fun getId(context: Context, identifier: String): Int {
		return getResourceId(context, identifier, ResourceTypes.ID)
	}

	fun getRawId(context: Context, identifier: String): Int {
		return getResourceId(context, identifier, ResourceTypes.RAW)
	}

	object ResourceTypes {
		const val LAYOUT: String = "layout"
		const val ID: String = "id"
		const val DRAWABLE: String = "drawable"
		const val RAW: String = "raw"
	}
}