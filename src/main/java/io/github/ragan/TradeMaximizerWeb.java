package io.github.ragan;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class TradeMaximizerWeb {

    public static void main(String[] args) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("", 12345), 0);
        httpServer.createContext("/", new TradeMaximizerHandler());
        httpServer.setExecutor(null);
        httpServer.start();
    }

}
