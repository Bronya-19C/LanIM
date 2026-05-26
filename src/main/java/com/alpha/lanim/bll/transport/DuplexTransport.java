package com.alpha.lanim.bll.transport;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;

public interface DuplexTransport extends Closeable {

    void send(byte[] data) throws IOException;

    byte[] receive() throws IOException;

    Socket getSocket();

    boolean isClosed();

    @Override
    void close();
}
