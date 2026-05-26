package com.alpha.lanim.bll;

import com.alpha.lanim.dal.PeerDao;
import com.alpha.lanim.model.Peer;
import com.alpha.lanim.util.Constants;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PeerService {

    private final PeerDao peerDao;
    private String localPeerId;
    private String nickname;
    private String roomId;
    private String address;
    private int port;
    private final Map<String, Peer> remotePeers;

    public PeerService() {
        this.peerDao = new PeerDao();
        this.remotePeers = new ConcurrentHashMap<>();
        this.localPeerId = UUID.randomUUID().toString();
        this.nickname = Constants.DEFAULT_NICKNAME_PREFIX +
                Integer.toHexString((int) (Math.random() * 0xFFFF)).toUpperCase();
    }

    public void init(String nickname, String roomId, String address, int port) {
        this.nickname = nickname;
        this.roomId = roomId;
        this.address = address;
        this.port = port;
    }

    public String getLocalPeerId() { return localPeerId; }

    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getNickname() { return nickname; }

    public String getRoomId() { return roomId; }

    public String getAddress() { return address; }
    public int getPort() { return port; }

    public Peer getLocalPeer() {
        Peer p = new Peer(localPeerId, nickname, address, port);
        p.setLastSeen(System.currentTimeMillis());
        return p;
    }

    public void addRemotePeer(Peer peer) {
        remotePeers.put(peer.getPeerId(), peer);
        peerDao.upsert(peer);
    }

    public void removeRemotePeer(String peerId) {
        remotePeers.remove(peerId);
    }

    public Peer getRemotePeer(String peerId) {
        return remotePeers.get(peerId);
    }

    public List<Peer> getRemotePeers() {
        return List.copyOf(remotePeers.values());
    }

    public int getRemotePeerCount() {
        return remotePeers.size();
    }

    public boolean hasRemotePeer(String peerId) {
        return remotePeers.containsKey(peerId);
    }
}
