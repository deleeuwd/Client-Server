package bgu.spl.net.impl.tftp;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class TftfUserManager {
    private static TftfUserManager instance = null;
    private ConcurrentHashMap<String, Integer> usernames;

    private TftfUserManager() {
        usernames = new ConcurrentHashMap<>();
    }

    public static TftfUserManager getInstance() {
        if (instance == null) {
            instance = new TftfUserManager();
        }
        return instance;
    }

    public boolean isUsernameExists(String nickname) {
        return usernames.containsKey(nickname);
    }

    public void addUsername(String nickname, int connectionId) {
        usernames.put(nickname, connectionId);
    }

    public void removeUsername(String nickname) {
        usernames.remove(nickname);
    }

    // Get all the connectionids
    public Collection<Integer> getAllConnections() {
        return usernames.values();
    }
}
