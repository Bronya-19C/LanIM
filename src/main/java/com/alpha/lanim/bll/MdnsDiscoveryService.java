package com.alpha.lanim.bll;

import com.alpha.lanim.model.Peer;
import com.alpha.lanim.util.Constants;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

public class MdnsDiscoveryService {

    private final PeerService peerService;
    private JmDNS jmdns;
    private ServiceInfo ownService;
    private volatile boolean running;

    public MdnsDiscoveryService(PeerService peerService) {
        this.peerService = peerService;
    }

    public void start(int tcpPort) throws IOException {
        jmdns = JmDNS.create();
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
                jmdns.requestServiceInfo(Constants.MDNS_SERVICE_TYPE, event.getName());
            }

            @Override
            public void serviceRemoved(ServiceEvent event) {
                String peerId = event.getInfo() != null
                        ? event.getInfo().getPropertyString(Constants.TXT_ROOM_ID)
                        : null;
                if (peerId != null) {
                    peerService.removeRemotePeer(peerId);
                }
            }

            @Override
            public void serviceResolved(ServiceEvent event) {
                ServiceInfo info = event.getInfo();
                if (info == null) return;

                String roomId = info.getPropertyString(Constants.TXT_ROOM_ID);
                if (roomId == null || !roomId.equals(peerService.getRoomId())) {
                    return;
                }

                String nickname = info.getPropertyString(Constants.TXT_NICKNAME);
                String address = info.getInetAddresses().length > 0
                        ? info.getInetAddresses()[0].getHostAddress()
                        : info.getHostAddress();
                int port = info.getPort();

                String peerId = event.getName();
                if (peerId == null || peerId.equals("LANIM-" + peerService.getLocalPeerId())) {
                    return;
                }

                // Extract actual peerId from service name "LANIM-<peerId>"
                if (peerId.startsWith("LANIM-")) {
                    peerId = peerId.substring(6);
                }

                Peer peer = new Peer(peerId, nickname, address, port);
                peer.setLastSeen(System.currentTimeMillis());
                peerService.addRemotePeer(peer);
            }
        });

        // Browse for existing services
        jmdns.requestServiceInfo(Constants.MDNS_SERVICE_TYPE, null);
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
