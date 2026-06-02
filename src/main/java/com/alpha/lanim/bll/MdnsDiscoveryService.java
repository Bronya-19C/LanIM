package com.alpha.lanim.bll;

import com.alpha.lanim.model.Peer;
import com.alpha.lanim.util.Constants;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

public class MdnsDiscoveryService {

    private final PeerService peerService;
    private final InetAddress localAddress;
    private JmDNS jmdns;
    private ServiceInfo ownService;
    private volatile boolean running;

    public MdnsDiscoveryService(PeerService peerService, InetAddress localAddress) {
        this.peerService = peerService;
        this.localAddress = localAddress;
    }

    public void start(int tcpPort) throws IOException {
        jmdns = JmDNS.create(localAddress);
        running = true;

        Map<String, String> props = new HashMap<>();
        props.put(Constants.TXT_ROOM_ID, peerService.getRoomId());
        props.put(Constants.TXT_NICKNAME, peerService.getNickname());

        ownService = ServiceInfo.create(
                Constants.MDNS_SERVICE_TYPE,
                "LANIM-" + peerService.getLocalPeerId(),
                tcpPort,
                0, 0,
                props
        );

        jmdns.registerService(ownService);

        jmdns.addServiceListener(Constants.MDNS_SERVICE_TYPE, new ServiceListener() {
            @Override
            public void serviceAdded(ServiceEvent event) {
                jmdns.requestServiceInfo(event.getType(), event.getName());
            }

            @Override
            public void serviceRemoved(ServiceEvent event) {
                String peerId = parsePeerIdFromServiceName(event.getName());
                if (peerId != null) {
                    peerService.removeRemotePeer(peerId);
                }
            }

            @Override
            public void serviceResolved(ServiceEvent event) {
                if (event.getInfo() != null) {
                    registerPeerFromService(event.getInfo(), event.getName());
                }
            }
        });

        for (ServiceInfo info : jmdns.list(Constants.MDNS_SERVICE_TYPE)) {
            if (info != null && info.getName() != null) {
                jmdns.requestServiceInfo(info.getType(), info.getName());
            }
        }
    }

    private void registerPeerFromService(ServiceInfo info, String serviceName) {
        String roomId = info.getPropertyString(Constants.TXT_ROOM_ID);
        if (roomId == null || !roomId.equals(peerService.getRoomId())) {
            return;
        }

        String peerId = parsePeerIdFromServiceName(serviceName);
        if (peerId == null || peerId.equals(peerService.getLocalPeerId())) {
            return;
        }

        String nickname = info.getPropertyString(Constants.TXT_NICKNAME);
        String address = info.getInetAddresses().length > 0
                ? info.getInetAddresses()[0].getHostAddress()
                : info.getHostAddress();
        int port = info.getPort();

        Peer peer = new Peer(peerId, nickname, address, port);
        peer.setLastSeen(System.currentTimeMillis());
        peerService.addRemotePeer(peer);
    }

    private static String parsePeerIdFromServiceName(String serviceName) {
        if (serviceName == null || !serviceName.startsWith("LANIM-")) {
            return null;
        }
        return serviceName.substring(6);
    }

    public void stop() {
        running = false;
        if (jmdns != null) {
            if (ownService != null) {
                jmdns.unregisterService(ownService);
            }
            try { jmdns.close(); } catch (IOException ignored) {}
            jmdns = null;
        }
    }

    public boolean isRunning() {
        return running;
    }
}
