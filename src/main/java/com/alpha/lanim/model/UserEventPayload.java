package com.alpha.lanim.model;

public class UserEventPayload {

    private String peerId;
    private String nickname;

    public UserEventPayload() {}

    public UserEventPayload(String peerId, String nickname) {
        this.peerId = peerId;
        this.nickname = nickname;
    }

    public String getPeerId() { return peerId; }
    public void setPeerId(String peerId) { this.peerId = peerId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
