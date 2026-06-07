package com.alpha.lanim.model;

import java.util.List;

public class JoinAckPayload {

    private String assignedId;
    private String roomId;
    private List<Envelope> history;
    private List<MemberInfo> members;

    public JoinAckPayload() {}

    public JoinAckPayload(String assignedId, String roomId,
                          List<Envelope> history, List<MemberInfo> members) {
        this.assignedId = assignedId;
        this.roomId = roomId;
        this.history = history;
        this.members = members;
    }

    public String getAssignedId() { return assignedId; }
    public void setAssignedId(String assignedId) { this.assignedId = assignedId; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public List<Envelope> getHistory() { return history; }
    public void setHistory(List<Envelope> history) { this.history = history; }

    public List<MemberInfo> getMembers() { return members; }
    public void setMembers(List<MemberInfo> members) { this.members = members; }

    public static class MemberInfo {
        private String peerId;
        private String nickname;

        public MemberInfo() {}

        public MemberInfo(String peerId, String nickname) {
            this.peerId = peerId;
            this.nickname = nickname;
        }

        public String getPeerId() { return peerId; }
        public void setPeerId(String peerId) { this.peerId = peerId; }

        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
    }
}
