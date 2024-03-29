package com.hao.imageload;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

public class SquareImage extends android.support.v7.widget.AppCompatImageView {
    public SquareImage(Context context) {
        super(context);
    }

    public SquareImage(Context context,  AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareImage(Context context,  AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }
}
