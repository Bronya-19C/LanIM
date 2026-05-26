package com.alpha.lanim.bll;

import com.alpha.lanim.dal.MessageDao;
import com.alpha.lanim.model.*;
import com.alpha.lanim.util.Constants;
import com.alpha.lanim.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SyncService {

    private final PeerService peerService;
    private final MessageDao messageDao;
    private TcpChatService tcpChatService;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Integer> lastSeenFromPeer;
    private final Object seqLock = new Object();
    private int localSequence;
    private boolean started;

    public SyncService(PeerService peerService) {
        this.peerService = peerService;
        this.messageDao = new MessageDao();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("sync-scheduler");
            return t;
        });
        this.lastSeenFromPeer = new ConcurrentHashMap<>();
        this.localSequence = 0;
    }

    public void setTcpChatService(TcpChatService tcpChatService) {
        this.tcpChatService = tcpChatService;
    }

    public void init() {
        this.localSequence = messageDao.getNextSequence(peerService.getRoomId(), peerService.getLocalPeerId());
    }

    public void start() {
        if (started) return;
        started = true;
        scheduler.scheduleWithFixedDelay(
                this::syncWithAllPeers,
                Constants.DEFAULT_SYNC_INTERVAL_MS,
                Constants.DEFAULT_SYNC_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void syncWithAllPeers() {
        List<Peer> peers = peerService.getRemotePeers();
        for (Peer peer : peers) {
            if (tcpChatService.isConnected(peer.getPeerId())) {
                try {
                    sendSyncRequest(peer.getPeerId());
                } catch (Exception e) {
                    System.err.println("Sync failed for " + peer.getPeerId() + ": " + e.getMessage());
                }
            }
        }
    }

    private void sendSyncRequest(String targetPeerId) {
        SyncReqPayload payload = new SyncReqPayload(new HashMap<>(lastSeenFromPeer));
        String messageId = UUID.randomUUID().toString();

        Envelope envelope = new Envelope(
                MessageType.SYNC_REQ.name(),
                messageId,
                peerService.getLocalPeerId(),
                peerService.getRoomId(),
                0,
                System.currentTimeMillis(),
                JsonUtil.gson().toJsonTree(payload).getAsJsonObject()
        );

        tcpChatService.sendToPeer(targetPeerId, envelope);
    }

    public void handleSyncRequest(Envelope request) {
        SyncReqPayload payload = JsonUtil.fromPayload(request.getPayload(), SyncReqPayload.class);
        Map<String, Integer> theirLastSeen = payload.getLastSequences();

        List<Envelope> unseen = new ArrayList<>();
        String roomId = peerService.getRoomId();

        for (Map.Entry<String, Integer> entry : theirLastSeen.entrySet()) {
            String senderId = entry.getKey();
            int since = entry.getValue();
            List<Envelope> msgs = messageDao.findBySenderAfter(roomId, senderId, since);
            unseen.addAll(msgs);
        }

        // Also send our own messages they haven't seen
        int mySince = theirLastSeen.getOrDefault(peerService.getLocalPeerId(), -1);
        unseen.addAll(messageDao.findBySenderAfter(roomId, peerService.getLocalPeerId(), mySince));

        SyncRespPayload respPayload = new SyncRespPayload(unseen);
        Envelope response = new Envelope(
                MessageType.SYNC_RESP.name(),
                UUID.randomUUID().toString(),
                peerService.getLocalPeerId(),
                roomId,
                0,
                System.currentTimeMillis(),
                JsonUtil.gson().toJsonTree(respPayload).getAsJsonObject()
        );

        tcpChatService.sendToPeer(request.getSenderId(), response);
    }

    public void handleSyncResponse(Envelope response) {
        SyncRespPayload payload = JsonUtil.fromPayload(response.getPayload(), SyncRespPayload.class);
        if (payload.getMessages() != null) {
            for (Envelope msg : payload.getMessages()) {
                messageDao.insert(msg);
                // Update waterline
                Integer current = lastSeenFromPeer.getOrDefault(msg.getSenderId(), -1);
                if (msg.getSequence() > current) {
                    lastSeenFromPeer.put(msg.getSenderId(), msg.getSequence());
                }
            }
        }
    }

    public int nextSequence() {
        synchronized (seqLock) {
            int seq = localSequence;
            localSequence++;
            return seq;
        }
    }

    public void recordOutgoingMessage(Envelope envelope) {
        if (envelope.getType().equals(MessageType.CHAT_TEXT.name()) ||
                envelope.getType().equals(MessageType.FILE_META.name())) {
            messageDao.insert(envelope);
            lastSeenFromPeer.put(peerService.getLocalPeerId(), envelope.getSequence());
        }
    }
}
