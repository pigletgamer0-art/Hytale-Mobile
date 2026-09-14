package com.alonstark.tricontroller;

public enum ControllerProfile {
    XBOX("Xbox", "TriConsole Xbox Controller", new String[]{"A", "B", "X", "Y"}),
    PLAYSTATION("PlayStation", "TriConsole PlayStation Controller", new String[]{"×", "○", "□", "△"}),
    NINTENDO("Nintendo", "TriConsole Nintendo Controller", new String[]{"B", "A", "Y", "X"});

    public final String title;
    public final String hidName;
    public final String[] faceLabels; // south, east, west, north

    ControllerProfile(String title, String hidName, String[] faceLabels) {
        this.title = title;
        this.hidName = hidName;
        this.faceLabels = faceLabels;
    }

    public String backLabel() {
        switch (this) {
            case PLAYSTATION: return "Create";
            case NINTENDO: return "−";
            default: return "View";
        }
    }

    public String startLabel() {
        switch (this) {
            case PLAYSTATION: return "Options";
            case NINTENDO: return "+";
            default: return "Menu";
        }
    }

    public String guideLabel() {
        switch (this) {
            case PLAYSTATION: return "PS";
            case NINTENDO: return "HOME";
            default: return "XBOX";
        }
    }

    public String extraLabel() {
        switch (this) {
            case PLAYSTATION: return "Touchpad";
            case NINTENDO: return "Capture";
            default: return "Share";
        }
    }

    public String leftBumperLabel() {
        switch (this) {
            case PLAYSTATION: return "L1";
            case NINTENDO: return "L";
            default: return "LB";
        }
    }

    public String rightBumperLabel() {
        switch (this) {
            case PLAYSTATION: return "R1";
            case NINTENDO: return "R";
            default: return "RB";
        }
    }

    public String leftTriggerLabel() {
        switch (this) {
            case PLAYSTATION: return "L2";
            case NINTENDO: return "ZL";
            default: return "LT";
        }
    }

    public String rightTriggerLabel() {
        switch (this) {
            case PLAYSTATION: return "R2";
            case NINTENDO: return "ZR";
            default: return "RT";
        }
    }

    public String specialHint() {
        switch (this) {
            case PLAYSTATION:
                return "Especial PS: Create → captura · Touchpad → cursor/clic · PS → Guide";
            case NINTENDO:
                return "Especial Nintendo: Capture → captura · HOME → Guide";
            default:
                return "Especial Xbox: Share → captura · XBOX → Guide";
        }
    }
}
