package edu.osu.pcv.marslogger;

public class ManualFocusConfig {
    final float mEventX;
    final float mEventY;
    final int mViewWidth;
    final int mViewHeight;
    public ManualFocusConfig(float eventX, float eventY, int viewWidth, int viewHeight) {
        mEventX = eventX;
        mEventY = eventY;
        mViewWidth = viewWidth;
        mViewHeight = viewHeight;
    }
    @Override
    public String toString() {
        return "ManualFocusConfig: " + mViewWidth + "x" + mViewHeight + " @ " + mEventX +
                "," + mEventY;
    }
}
