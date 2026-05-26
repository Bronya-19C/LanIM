package com.alpha.lanim.bll.transport;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class PlainTcpTransport implements DuplexTransport {

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private volatile boolean closed;

    public PlainTcpTransport(Socket socket) throws IOException {
        this.socket = socket;
        if (socket != null) {
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        } else {
            this.in = null;
            this.out = null;
        }
        this.closed = false;
    }

    @Override
    public void send(byte[] data) throws IOException {
        if (out == null) throw new IOException("Transport not connected");
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    @Override
    public byte[] receive() throws IOException {
        if (in == null) throw new IOException("Transport not connected");
        int length = in.readInt();
        byte[] data = new byte[length];
        in.readFully(data);
        return data;
    }

    @Override
    public Socket getSocket() {
        return socket;
    }

    @Override
    public boolean isClosed() {
        return closed || (socket != null && socket.isClosed());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
