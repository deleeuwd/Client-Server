package bgu.spl.net.srv;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {
    private final Map<Integer, ConnectionHandler<T>> clients= new ConcurrentHashMap<>();

    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler){
        if(clients.containsKey(connectionId)){
            return;
        }
        else{
            clients.put(connectionId, handler);

        }
    }

    @Override
    public boolean send(int connectionId, T msg){
        ConnectionHandler<T> handler = clients.get(connectionId);
        
        if (handler != null) {
            handler.send(msg); // implement in the blocking connection handler
            return true;
        }
        return false; 
    }

    @Override
    public void disconnect(int connectionId){
        clients.remove(connectionId);
    }
}
