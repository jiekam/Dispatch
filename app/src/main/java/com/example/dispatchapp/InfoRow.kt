package com.example.dispatchapp

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

class InfoRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val iconView: ImageView
    private val labelView: TextView
    private val valueView: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_info_row, this, true)
        iconView = findViewById(R.id.iv_info_icon)
        labelView = findViewById(R.id.tv_info_label)
        valueView = findViewById(R.id.tv_info_value)

        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.InfoRow, defStyleAttr, 0)
            val iconRes = typedArray.getResourceId(R.styleable.InfoRow_icon, 0)
            val label = typedArray.getText(R.styleable.InfoRow_label)
            val value = typedArray.getText(R.styleable.InfoRow_value)
            typedArray.recycle()

            if (iconRes != 0) iconView.setImageResource(iconRes)
            labelView.text = label ?: ""
            valueView.text = value ?: ""
        }
    }

    fun setValue(text: String) { valueView.text = text }
    fun setLabel(text: String) { labelView.text = text }
}
