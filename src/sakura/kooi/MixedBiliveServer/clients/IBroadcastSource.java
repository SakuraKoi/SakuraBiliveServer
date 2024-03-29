package sakura.kooi.MixedBiliveServer.clients;

import java.io.IOException;

public interface IBroadcastSource {
    public void connect() throws IOException;
    public void disconnect(String reason) throws IOException;

    public void processPacket(String packet);
}
