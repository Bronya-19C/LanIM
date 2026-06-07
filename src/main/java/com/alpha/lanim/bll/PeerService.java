package com.alpha.lanim.bll;

import com.alpha.lanim.dal.PeerDao;
import com.alpha.lanim.model.Peer;
import com.alpha.lanim.util.Constants;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PeerService {

    private final PeerDao peerDao;
    private String localPeerId;
    private String nickname;
    private String roomId;
    private final Map<String, Peer> remotePeers;
    private final List<Runnable> peerChangeListeners;

    public PeerService() {
        this.peerDao = new PeerDao();
        this.remotePeers = new ConcurrentHashMap<>();
        this.localPeerId = UUID.randomUUID().toString();
        this.nickname = Constants.DEFAULT_NICKNAME_PREFIX +
                Integer.toHexString((int) (Math.random() * 0xFFFF)).toUpperCase();
        this.peerChangeListeners = Collections.synchronizedList(new ArrayList<>());
    }

    public void init(String nickname, String roomId) {
        this.nickname = nickname;
        this.roomId = roomId;
    }

    public String getLocalPeerId() { return localPeerId; }

    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getNickname() { return nickname; }

    public String getRoomId() { return roomId; }

    public Peer getLocalPeer() {
        Peer p = new Peer(localPeerId, nickname, "", 0);
        p.setLastSeen(System.currentTimeMillis());
        return p;
    }

    public void addRemotePeerById(String peerId, String nickname) {
        Peer peer = new Peer(peerId, nickname, "", 0);
        remotePeers.put(peerId, peer);
        peerDao.upsert(peer);
        notifyPeerChange();
    }

    public void removeRemotePeer(String peerId) {
        remotePeers.remove(peerId);
        notifyPeerChange();
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

    public void addPeerChangeListener(Runnable listener) {
        peerChangeListeners.add(listener);
    }

    private void notifyPeerChange() {
        for (Runnable listener : peerChangeListeners) {
            listener.run();
        }
    }
}
