package com.example.dispatchapp

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MenuItemLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val iconView: ImageView
    private val titleView: TextView
    private val divider: View

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_menu_item, this, true)
        
        iconView = findViewById(R.id.iv_menu_icon)
        titleView = findViewById(R.id.tv_menu_title)
        divider = findViewById(R.id.divider)

        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.MenuItemLayout, defStyleAttr, 0)
            
            val iconRes = typedArray.getResourceId(R.styleable.MenuItemLayout_icon, 0)
            val title = typedArray.getString(R.styleable.MenuItemLayout_title)
            val showDivider = typedArray.getBoolean(R.styleable.MenuItemLayout_showDivider, true)
            
            if (iconRes != 0) iconView.setImageResource(iconRes)
            titleView.text = title ?: ""
            divider.visibility = if (showDivider) VISIBLE else GONE
            
            typedArray.recycle()
        }
    }

    fun setTitle(title: String) {
        titleView.text = title
    }
}
