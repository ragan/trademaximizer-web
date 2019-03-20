package io.github.ragan;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.ragan.trademaximizer.TradeMaximizer;

import java.io.*;

public class TradeMaximizerHandler implements HttpHandler {

    private final TradeMaximizer tradeMaximizer = new TradeMaximizer();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(200, 0);
        OutputStream tos = teeOutputStream(exchange.getResponseBody(), System.out);

        tradeMaximizer.run(new String[]{}, exchange.getRequestBody(), tos);
        tos.write("\ntest\n\n".getBytes());
        tos.close();
    }

    static OutputStream teeOutputStream(OutputStream os, OutputStream branch) {
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                os.write(b);
                branch.write(b);
            }

            @Override
            public void write(byte[] b) throws IOException {
                os.write(b);
                branch.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                os.write(b, off, len);
                branch.write(b, off, len);
            }

            @Override
            public void close() throws IOException {
                os.close();
                branch.close();
            }

            @Override
            public void flush() throws IOException {
                os.flush();
                branch.flush();
            }
        };
    }
}

