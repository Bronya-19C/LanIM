package com.alpha.lanim.util;

public final class Constants {

    public static final String TRANSPORT_MODE_TLS = "tls";
    public static final String TRANSPORT_MODE_PLAIN = "plain";
    public static final String DEFAULT_TRANSPORT_MODE = TRANSPORT_MODE_TLS;

    public static final int DEFAULT_SERVER_PORT = 9090;

    public static final String DEFAULT_DB_PATH = "data/lanim.db";
    public static final String JDBC_PREFIX = "jdbc:sqlite:";

    public static final String DEFAULT_FILES_PATH = "data/files";
    public static final int FILE_CHUNK_SIZE = 65536;

    public static final String DEFAULT_KEYSTORE_PATH = "config/keystore.jks";
    public static final String TRUSTED_PEERS_PATH = "config/trusted_peers.json";

    public static final String DEFAULT_NICKNAME_PREFIX = "User-";

    public static final int MAX_NICKNAME_LENGTH = 32;
    public static final int MAX_TEXT_LENGTH = 4096;

    private Constants() {}
}
