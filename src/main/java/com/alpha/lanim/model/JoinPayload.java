package com.alpha.lanim.model;

public class JoinPayload {

    private String peerId;
    private String nickname;
    private String roomSecret;

    public JoinPayload() {}

    public JoinPayload(String peerId, String nickname, String roomSecret) {
        this.peerId = peerId;
        this.nickname = nickname;
        this.roomSecret = roomSecret;
    }

    public String getPeerId() { return peerId; }
    public void setPeerId(String peerId) { this.peerId = peerId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getRoomSecret() { return roomSecret; }
    public void setRoomSecret(String roomSecret) { this.roomSecret = roomSecret; }
}
