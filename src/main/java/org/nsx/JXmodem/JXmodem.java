/*
 * Copyright 2021 Samuel Hobson
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.nsx.JXmodem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This is a port of Georges Menie's Xmodem program to Java, adapted to use Java
 * classes (particularly streams) and with support for XModem-1K. This library
 * is designed to automatically use Xmodem-1K, CRC-Xmodem, or Xmodem depending
 * on what the other device supports.
 * <p>
 * M. Menie's original C code lives here:
 * https://www.menie.org/georges/embedded/xmodem.html
 */
public class JXmodem {
    /** max times to retry sending one packet */
    private final int MAXERRORS = 25;
    /** Offset for data payload in packet */
    private final int DATAOFFSET = 3;

    /** Start of header */
    private final byte SOH = 0x1;
    /** Start of header for XModem-1K */
    private final byte STX = 0x2;
    /** end of transmission */
    private final byte EOT = 0x4;
    /** packet acknowledged */
    private final byte ACK = 0x6;
    /** packet not acknowledged */
    private final byte NAK = 0x15;
    /** Cancel signal */
    private final byte CAN = 0x18;
    /** End of file */
    private final byte EOF = 0x1A;
    /** Xmodem-CRC ready signal (aka the letter 'C') */
    private final byte XMODEMCRC = 0x43;

    private final InputStream inStream;
    private final OutputStream outStream;

    /**
     * Create a new jXmodem instance, which will allow data transfer over the
     * given streams using Xmodem protocols. Things like baud rate and such
     * are expected to be handled by the streams ahead of time.
     * 
     * @param input  The stream to read data from. Any data sent from the other
     *               device will be read from this stream.
     * @param output The stream to write data to. Data sent to the other device
     *               gets written to this stream.
     */
    public JXmodem(InputStream input, OutputStream output)
    {
        inStream = input;
        outStream = output;
    }


    /**
     * Attempt to receive a message from the transmitter. The caller of this
     * function shall be the receiver, and direct the flow of the transmission.
     * If a message is successfully received, then it will be returned as a
     * String encoded with the platform's default character set. If a message
     * cannot be received, or an Xmodem-related error occurs at any point during
     * the transmission, then the function shall return null.
     * 
     * @return             Upon successful transmission, the received message as
     *                     a string. Otherwise, null.
     * @throws IOException If any non-Xmodem-related I/O error occurs. See
     *                     <a href="#{@link}">{@link InputStream}</a> and
     *                     <a href="#{@link}">{@link OutputStream}</a> for more
     *                     information.
     */
    public String receiveString() throws IOException
    {
        byte[] message = receive();
        return message == null ? null : new String(message);
    }


    /**
     * Attempt to receive a message from the transmitter. The caller of this
     * function shall be the receiver, and direct the flow of the transmission.
     * If a message is successfully received, then it will be returned as a
     * String encoded with the given character set. If a message cannot be
     * received, or an Xmodem-related error occurs at any point during the
     * transmission, then the function shall return null.
     * 
     * @param  charset     the character set with which to encode the received 
     *                     message.
     * @return             Upon successful transmission, the received message as
     *                     a string. Otherwise, null.
     * @throws IOException If any non-Xmodem-related I/O error occurs. See
     *                     <a href="#{@link}">{@link InputStream}</a> and
     *                     <a href="#{@link}">{@link OutputStream}</a> for more
     *                     information.
     */
    public String receiveString(String charset) throws IOException
    {
        byte[] message = receive();
        return message == null ? null : new String(message, charset);
    }


    /**
     * Attempt to receive a file from the transmitter. The caller of this
     * function shall be the receiver, and direct the flow of the transmission.
     * If a file is successfully received, then it will be written to local
     * storage at the given savePath, and the function will return true to
     * indicate a successful transfer. If a file cannot be received, or an
     * Xmodem-related error occurs at any point during the transmission, then
     * the function will return false. 
     * <p>
     * If any error related to writing the file
     * occurs, then a FileNotFoundException will be thrown. See
     * <a href="#{@link}">{@link FileOutputStream}</a> for more information.
     * 
     * @param  savePath    the path where the file should be saved
     * @return             True if the transmission succeeded, false otherwise
     * @throws IOException If any non-Xmodem-related I/O error occurs. See
     *                     <a href="#{@link}">{@link InputStream}</a> and
     *                     <a href="#{@link}">{@link OutputStream}</a> for more
     *                     information.
     */
    public boolean receiveFile(String savePath) throws IOException
    {
        byte[] data = receive();
        if (data == null)
            return false;

        // with files we don't want to write the EOF or the padding afterward
        int i = data.length;
        while(data[--i] != EOF);
        try (var fileOutStream = new FileOutputStream(savePath)) {
            fileOutStream.write(data, 0, i);
        }
        return true;
    }


    /**
     * Attempt to receive a message from the transmitter. The caller of this
     * function shall be the receiver, and direct the flow of the transmission.
     * If a message is successfully received, then it will be returned as a byte
     * array. If a message cannot be received, or an Xmodem-related error
     * occurs at any point during the transmission, then the function shall
     * return null.
     * 
     * @return             Upon successful transmission, a byte array containing
     *                     the entire message. Otherwise, null.
     * @throws IOException If any non-Xmodem-related I/O error occurs. See
     *                     <a href="#{@link}">{@link InputStream}</a> and
     *                     <a href="#{@link}">{@link OutputStream}</a> for more
     *                     information.
     */
    public byte[] receive() throws IOException
    {
        ByteArrayOutputStream msgBuffer = new ByteArrayOutputStream();

        Boolean useCRC = true;
        byte helloMsg = XMODEMCRC;
        byte packetNumber = 1;
        int errorCt = 0;

        mainLoop:
        while (errorCt < MAXERRORS) {
            Boolean use1K = null;
            Byte c = null;
            helloLoop:
            for (int i = 0; i < 10; ++i) {
                if (helloMsg != 0) {
                    System.out.printf("R: sending 0x%02X%n", helloMsg);
                    outStream.write(helloMsg);
                }

                c = readByte(10000);
                System.out.printf("R: first received 0x%02X%n", c);
                if (c == null)
                    continue;

                switch (c) {
                case SOH:
                    use1K = false;
                    helloMsg = 0;
                    System.out.printf("R: using Xmodem%s%n",useCRC?"-CRC":"");
                    break helloLoop;
                case STX:
                    use1K = true;
                    helloMsg = 0;
                    System.out.println("R: using Xmodem-1K");
                    break helloLoop;
                case EOT:
                    // We've got the entire message. Acknowledge and return the
                    // message as a byte array.
                    System.out.println("R: received EOT. Ending.");
                    outStream.write(ACK);
                    return msgBuffer.toByteArray();
                case CAN:
                    // sometimes CAN comes in as a result of garbled data.
                    // look for a second cancel as confirmation.
                    c = readByte(1000);
                    if (c != null && c == CAN) {
                        System.out.println("R: Cancel received, terminating.");
                        outStream.write(ACK);
                        return null;
                    }
                default:
                    break;
                }
            }

            if (helloMsg == XMODEMCRC) {
                // transmitter doesn't support CRC mode. Fall back to Xmodem.
                System.out.println();
                helloMsg = NAK;
                useCRC = false;
                continue;
            }
            else if (helloMsg == NAK || c == null) {
                // either our hello wasn't acknowledged or we never started 
                // getting a packet. Cancel
                System.out.printf("R: helloMsg=0x%02X use1K=%s%n",helloMsg,use1K);
                break;
            }

            int maxDataSize = use1K ? 1024 : 128;
            byte[] packet = new byte[1030];
            int packetSize = DATAOFFSET + maxDataSize + (useCRC ? 1 : 0);

            // receive the packet
            int bytesRead = 0;
            System.out.printf("R: receiving packet %d...", packetNumber);
            while (bytesRead < packetSize) {
                c = readByte(2000);
                if (c == null) {
                    // We failed to get a byte. Write NAK and continue.
                    System.out.println("failure");
                    outStream.write(NAK);
                    continue mainLoop;
                }
                packet[++bytesRead] = c.byteValue();
            }
            System.out.println("Success");

            // Let's make sure our packet is good. Packet is good if:
            // 1) packet numbers match or packet is a repeat of the previous one
            // 2) packet number XOR its complement is 0xFF
            // 3) CRCs or checksums match
            boolean good = packet[1] == packetNumber;
            good = good || packet[1] == packetNumber - 1;
            good = good && packet[2] == (byte) (~packet[1]);

            if (useCRC) {
                char crc = CRC16.calculate(
                    packet,
                    DATAOFFSET,
                    maxDataSize
                );
                byte crcHigh = (byte) ((crc >> 8) & 0xFF);
                byte crcLow = (byte) (crc & 0xFF);
                good = good && packet[maxDataSize + 3] == crcHigh;
                good = good && packet[maxDataSize + 4] == crcLow;
            }
            else {
                byte csum = 0;
                for (int i = DATAOFFSET; i < maxDataSize + DATAOFFSET; ++i) {
                    csum += packet[i];
                }
                good = good && packet[maxDataSize + DATAOFFSET] == csum;
            }
            System.out.printf("R: Packet good: %B%n", good);

            if (good) {
                if (packet[1] == packetNumber) {
                    msgBuffer.write(packet, DATAOFFSET, maxDataSize);
                    errorCt = 0;
                    ++packetNumber;
                }
                else {
                    ++errorCt;
                }
                outStream.write(ACK);
            }
            else {
                outStream.write(NAK);
                continue;
            }
        } // while (errorCt < MAXERRORS)

        // If we're out here then we want to cancel. 
        System.out.println("R: Cancelling");
        outStream.write(CAN);
        outStream.write(CAN);
        outStream.write(CAN);

        return null;
    }


    /**
     * Transmit the file at the given location to the receiving device.
     * 
     * @param  filePath    The absolute or relative path to the file to be
     *                     transferred.
     * @return             True if the transmission went through successfully,
     *                     false
     *                     otherwise
     * @throws IOException if the file cannot be found, or if another I/O error
     *                     occurs. See
     *                     <a href="#{@link}">{@link InputStream}</a> and
     *                     <a href="#{@link}">{@link OutputStream}</a> for more
     *                     information.
     */
    public boolean sendFile(String filePath) throws IOException
    {
        return send(new FileInputStream(filePath));
    }


    /**
     * Transmit the given string to the receiving device. String will be encoded
     * using the default character set.
     * 
     * @param  message     The string to be transferred
     * @return             True if the transmission went through successfully,
     *                     false
     *                     otherwise
     * @throws IOException if an I/O error occurs. See
     *                     <a href="#{@link}">{@link InputStream}</a> and
     *                     <a href="#{@link}">{@link OutputStream}</a> for more
     *                     information.
     */
    public boolean sendString(String message) throws IOException
    {
        return send(new ByteArrayInputStream(message.getBytes()));
    }


    /**
     * Transmit the given string to the receiving device. String will be decoded
     * using the given character set. The function returns a boolean reflecting
     * whether or not the transmission was successful.
     * 
     * @param  message     The string to be transferred
     * @param  charset     the character set to use when decoding the string
     * @return             True if the transmission went through successfully,
     *                     false otherwise
     * @throws IOException if an I/O error occurs. See
     *                     <a href="#{@link}">{@link InputStream}</a> and
     *                     <a href="#{@link}">{@link OutputStream}</a> for more
     *                     information.
     */
    public boolean sendString(String message, String charset) throws IOException
    {
        return send(new ByteArrayInputStream(message.getBytes(charset)));
    }


    /**
     * Send the given byte array in its entirety to the receiving device. The
     * function returns a boolean reflecting whether or not the transmission was
     * successful.
     * 
     * @param  data        The array of bytes to be transferred
     * @return             True if the transmission went through successfully,
     *                     false
     *                     otherwise
     * @throws IOException if an I/O error occurs. See
     *                     <a href="#{@link}">{@link InputStream}</a> and
     *                     <a href="#{@link}">{@link OutputStream}</a> for more
     *                     information.
     */
    public boolean send(byte[] data) throws IOException
    {
        return send(new ByteArrayInputStream(data));
    }


    /**
     * Send the given byte array to the receiving device, sending length bytes
     * starting at offset. The function returns a boolean reflecting whether or
     * not the transmission was successful.
     * 
     * @param  data        The array of bytes to be transferred
     * @param  offset      The index of the array to begin writing from
     * @param  length      the number of bytes to write
     * @return             True if the transmission went through successfully,
     *                     false
     *                     otherwise
     * @throws IOException if an I/O error occurs. See
     *                     <a href="#{@link}">{@link InputStream}</a> and
     *                     <a href="#{@link}">{@link OutputStream}</a> for more
     *                     information.
     */
    public boolean send(byte[] data, int offset, int length) throws IOException
    {
        return send(new ByteArrayInputStream(data, offset, length));
    }


    /**
     * Attempt to transmit the data within the given dataStream over to the
     * receiving device. The function returns a boolean reflecting whether or
     * not the transmission was successful.
     * 
     * @param  dataStream  an InputStream object which contains the bytes that
     *                     will be transferred.
     * @return             True if the transmission went through successfully,
     *                     false otherwise
     * @throws IOException if an I/O error occurs. See
     *                     <a href="#{@link}">{@link InputStream}</a> and
     *                     <a href="#{@link}">{@link OutputStream}</a> for more
     *                     information.
     */
    private boolean send(InputStream dataStream) throws IOException
    {
        Boolean useCRC = null;
        try (dataStream) {
            System.out.println("T: awaiting initialization");
            helloLoop:
            for (int i = 0; i < 16; ++i) {
                Byte c = readByte(3000);
                // switch apparently can't handle nullable values
                if (c == null)
                    continue;
                switch (c) {
                case XMODEMCRC:
                    useCRC = true;
                    System.out.println("T: Using Xmodem CRC/1K");
                    break helloLoop;
                case NAK:
                    useCRC = false;
                    System.out.println("T: Using Xmodem");
                    break helloLoop;
                case CAN:
                    // sometimes CAN comes in as a result of garbled data.
                    // Look for a second cancel as confirmation.
                    c = readByte(1000);
                    if (c != null && c == CAN) {
                        System.out.println("T: Cancel received, terminating.");
                        outStream.write(ACK);
                        return false;
                    }
                default:
                    break;
                }
            }

            // if useCRC is null that means we never got a valid initiation.
            // tell receiver to cancel and return failure.
            if (useCRC == null) {
                outStream.write(CAN);
                outStream.write(CAN);
                outStream.write(CAN);
                return false;
            }

            // start the transmission
            byte packetNumber = 1;
            while (dataStream.available() > 0) {
                // xmodem-1k is an extension of xmodem crc
                // also if final packet is less than 1024 bytes, drop down to
                // xmodem crc to save bandwidth.
                boolean use1K = useCRC.booleanValue();
                use1K = use1K && dataStream.available() >= 1024;
                int maxDataSize = use1K ? 1024 : 128;

                // build the packet
                System.out.printf(
                    "\nT: Building Packet No. %d (payload is %d bytes)\n",
                    packetNumber,
                    maxDataSize
                );

                byte[] packet = new byte[1030];
                packet[0] = use1K ? STX : SOH;
                packet[1] = packetNumber;
                packet[2] = (byte) (~packetNumber & 0xFF);

                // DEBUG
                System.out.printf(
                    "T: Init: 0x%02X\nT: Packet: 0x%02X\nT: ~Packet: 0x%02X\n",
                    packet[0],
                    packet[1],
                    packet[2]
                );

                int bytesRead = dataStream.read(
                    packet,
                    DATAOFFSET,
                    maxDataSize
                );
                // append an EOF if we're at end of file
                if (bytesRead < maxDataSize)
                    packet[bytesRead + DATAOFFSET] = EOF;

                // DEBUG
                // System.out.print("T: Payload: ");
                // for (int i = DATAOFFSET; i < DATAOFFSET + maxDataSize; ++i) {
                //     System.out.printf("0x%02X ", packet[i]);
                // }
                // System.out.println();

                // put appropreate integrity check
                if (useCRC) {
                    char crc = CRC16.calculate(
                        packet,
                        DATAOFFSET,
                        maxDataSize
                    );
                    packet[maxDataSize + 3] = (byte) ((crc >> 8) & 0xFF);
                    packet[maxDataSize + 4] = (byte) (crc & 0xFF);
                    System.out.printf(
                        "T: CRC: 0x%02X 0x%02X\n",
                        packet[maxDataSize + 3],
                        packet[maxDataSize + 4]
                    );
                }
                else {
                    byte csum = 0;
                    for (int i =
                        DATAOFFSET; i < maxDataSize + DATAOFFSET; ++i) {
                        csum += packet[i];
                    }
                    packet[maxDataSize + DATAOFFSET] = csum;
                    System.out.printf(
                        "T: Checksum: 0x%02X\n",
                        packet[maxDataSize + DATAOFFSET]
                    );
                }

                // lets write the packet
                System.out.println("T: Sending...");
                int errorCt = 0;
                sendLoop:
                while (errorCt < MAXERRORS) {
                    // DEBUG
                    System.out.printf(
                        "T: Attempt %d of %d: ",
                        (errorCt + 1),
                        MAXERRORS
                    );

                    int packetSize = DATAOFFSET + maxDataSize;
                    packetSize += (useCRC ? 2 : 1);
                    for (int j = 0; j < packetSize; ++j) {
                        outStream.write(packet[j]);
                    }
                    outStream.flush();
                    System.out.println("Sent");

                    // read response
                    Byte c = readByte(30000);
                    System.out.printf("T: Received response 0x%02X\n", c);
                    if (c == null) {
                        ++errorCt;
                        continue;
                    }
                    switch (c) {
                    case ACK:
                        ++packetNumber;
                        break sendLoop;
                    case CAN:
                        c = readByte(1000);
                        if (c != null && c == CAN) {
                            outStream.write(ACK);
                            return false;
                        }
                    case NAK:
                    default:
                        break;
                    }

                    if (++errorCt >= MAXERRORS) {
                        outStream.write(CAN);
                        outStream.write(CAN);
                        outStream.write(CAN);
                        return false;
                    }
                }
            } // while (datastream.available > 0)

            // end transmission, 10 retries
            for (int i = 0; i < 10; ++i) {
                outStream.write(EOT);
                Byte c = readByte(2000);
                if (c != null && c == ACK) {
                    return true;
                }
            }
        }

        return false;
    }


    /**
     * Attempt to read a single byte from the input stream with the given
     * timeout
     * 
     * @param  timeout     timeout time in milliseconds
     * @return             a byte successfully read from the stream, or null if
     *                     timeout is reached
     * @throws IOException If the byte cannot be read for any reason other than
     *                     timeout or end of
     *                     file, if the input stream has been closed, or if some
     *                     other I/O error
     *                     occurs.
     */
    private Byte readByte(long timeout) throws IOException
    {
        byte[] b = new byte[1];
        boolean readSuccess = false;
        long stopTime = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < stopTime && !readSuccess) {
            int readLength = Math.min(1, inStream.available());
            int result = inStream.read(b, 0, readLength);
            readSuccess = result == 1;
        }
        return readSuccess ? b[0] : null;
    }
}
