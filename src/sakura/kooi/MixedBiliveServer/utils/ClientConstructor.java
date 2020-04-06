package sakura.kooi.MixedBiliveServer.utils;

import sakura.kooi.MixedBiliveServer.clients.ClientContainer;

import java.io.IOException;
import java.net.URISyntaxException;

public interface ClientConstructor<T> {
    public T create(ClientContainer container) throws IOException, URISyntaxException;
}
