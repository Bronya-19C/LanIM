package com.alpha.lanim.util;

public final class Constants {

    public static final String TRANSPORT_MODE_TLS = "tls";
    public static final String TRANSPORT_MODE_PLAIN = "plain";
    public static final String DEFAULT_TRANSPORT_MODE = TRANSPORT_MODE_TLS;

    public static final long DEFAULT_SYNC_INTERVAL_MS = 500;

    public static final String DEFAULT_DB_PATH = "data/lanim.db";
    public static final String JDBC_PREFIX = "jdbc:sqlite:";

    public static final String DEFAULT_FILES_PATH = "data/files";
    public static final int FILE_CHUNK_SIZE = 65536;

    public static final String DEFAULT_KEYSTORE_PATH = "config/keystore.jks";
    public static final String TRUSTED_PEERS_PATH = "config/trusted_peers.json";

    public static final String MDNS_SERVICE_TYPE = "_lanim._tcp.local.";
    public static final String TXT_ROOM_ID = "roomId";
    public static final String TXT_NICKNAME = "nickname";

    public static final String DEFAULT_NICKNAME_PREFIX = "User-";

    public static final int MAX_NICKNAME_LENGTH = 32;
    public static final int MAX_TEXT_LENGTH = 4096;

    private Constants() {}
}
