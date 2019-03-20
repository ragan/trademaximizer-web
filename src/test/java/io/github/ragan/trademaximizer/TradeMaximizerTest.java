package io.github.ragan.trademaximizer;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TradeMaximizerTest {

    @Test
    public void name() throws IOException {

        BufferedReader bufferedReader = new BufferedReader(new FileReader("src/test/resources/pref.txt"));
        StringBuilder stringBuilder = new StringBuilder();
        String line = null;
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        bufferedReader.close();

        new TradeMaximizer().run(new String[]{}, stringBuilder.toString());
    }
}