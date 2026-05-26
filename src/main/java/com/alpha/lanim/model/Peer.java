package com.alpha.lanim.model;

public class Peer {

    private String peerId;
    private String nickname;
    private String address;
    private int port;
    private String certFingerprint;
    private long lastSeen;

    public Peer() {}

    public Peer(String peerId, String nickname, String address, int port) {
        this.peerId = peerId;
        this.nickname = nickname;
        this.address = address;
        this.port = port;
    }

    public String getPeerId() { return peerId; }
    public void setPeerId(String peerId) { this.peerId = peerId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getCertFingerprint() { return certFingerprint; }
    public void setCertFingerprint(String certFingerprint) { this.certFingerprint = certFingerprint; }

    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Peer)) return false;
        Peer peer = (Peer) o;
        return peerId != null && peerId.equals(peer.peerId);
    }

    @Override
    public int hashCode() {
        return peerId != null ? peerId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return nickname + " (" + peerId + ")";
    }
}
