package com.minidex.app.input.adb;

interface IMouseControl {
    boolean isReady();
    void moveCursor(int deltaX, int deltaY);
    void setButton(int button, boolean pressed);
    void scroll(int vertical, int horizontal);
    boolean click(int displayId, float x, float y, int button);
    boolean keyPress(int displayId, int keyCode);
    void guardNextLaunch(int displayId);
    void setExclusiveDisplay(int displayId);
    void destroy();
}
