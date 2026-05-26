package com.alpha.lanim.model;

public class ChatPayload {
    private String text;

    public ChatPayload() {}

    public ChatPayload(String text) {
        this.text = text;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
