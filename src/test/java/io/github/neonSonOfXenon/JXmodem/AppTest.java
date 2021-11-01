package io.github.neonSonOfXenon.JXmodem;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.junit.jupiter.api.Test;

/**
 * Unit test for simple App.
 */
public class AppTest 
{
    @Test
    public void basicStringTransfer() 
    {
        var inStreamA = new PipedInputStream();
        var outStreamA = new PipedOutputStream();
        var inStreamB = new PipedInputStream();
        var outStreamB = new PipedOutputStream();

        try {
            inStreamB.connect(outStreamA);
            outStreamB.connect(inStreamA);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        Thread tA;
        Thread tB;

        // TEST SENDING STRING
        String testString =
            "All human beings are born free and equal in dignity and rights. They are endowed with reason and conscience and should act towards one another in a spirit of brotherhood.";

        tB = new Thread(new Runnable() {
            @Override
            public void run()
            {
                String result = "";
                try {
                    var xmB = new JXmodem(inStreamB, outStreamB);
                    result = xmB.receiveString();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                assertThat(result.equals(testString));
            }
        });

        tA = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try {
                    var xmA = new JXmodem(inStreamA, outStreamA);
                    xmA.sendString(testString);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        tA.start();
        tB.start();
        try {
            tA.join();
            tB.join();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
