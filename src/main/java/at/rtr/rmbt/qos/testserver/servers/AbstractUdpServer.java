/*******************************************************************************
 * Copyright 2016 Specure GmbH
 * Copyright 2016 Rundfunk und Telekom Regulierungs-GmbH (RTR-GmbH)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package at.rtr.rmbt.qos.testserver.servers;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import at.rtr.rmbt.qos.testserver.ServerPreferences.TestServerServiceEnum;
import at.rtr.rmbt.qos.testserver.TestServer;
import at.rtr.rmbt.qos.testserver.entity.Observable;
import at.rtr.rmbt.qos.testserver.entity.TestCandidate;
import at.rtr.rmbt.qos.testserver.udp.UdpTestCandidate;
import at.rtr.rmbt.qos.testserver.util.LineReader;
import at.rtr.rmbt.qos.testserver.util.TestServerConsole;
import at.rtr.rmbt.util.net.rtp.RealtimeTransportProtocol.RtpVersion;
import at.rtr.rmbt.util.net.rtp.RtpUtil;

public abstract class AbstractUdpServer<T extends Closeable> implements Runnable, Observable {
	
	protected final ConcurrentHashMap<String, UdpTestCandidate> incomingMap = new ConcurrentHashMap<>();
	
	protected final Class<?> clazz;
	
	public AbstractUdpServer(Class<?> clazz) {
		this.clazz = clazz;
	}
	
	public Class<?> getClazz() {
		return clazz;
	}

	/**
	 * 
	 * @param uuid
	 */
	public synchronized UdpTestCandidate getClientData(String uuid) {
		return incomingMap.get(uuid);
	}
	
	/**
	 * 
	 * @param uuid
	 * @return
	 */
	public synchronized TestCandidate pollClientData(String uuid) {
		return incomingMap.remove(uuid);
	}

	/**
	 * 
	 * @return
	 */
	public synchronized ConcurrentHashMap<String, UdpTestCandidate> getIncomingMap() {
		return incomingMap;
	}
	
	/**
	 * 
	 * @return
	 */
	/**
	 * length of the UDP test payload header: communication flag (1) + packet number (1) + client uuid (36)
	 */
	public final static int UDP_PAYLOAD_HEADER_LENGTH = 38;
	
	/**
	 * min length of an RTP packet (fixed header)
	 */
	public final static int RTP_HEADER_LENGTH = 12;
	
	/**
	 * Hands a received datagram to the test candidate it belongs to: UDP test packets are identified by the 
	 * client uuid (bytes 2-37), RTP packets by their SSRC. Packets for unknown candidates (i.e. without a 
	 * running test) and malformed packets are dropped, so unsolicited/spoofed traffic does not create any state.
	 * @param dp the received datagram (only dp.getLength() bytes are evaluated)
	 */
	protected void dispatchPacket(final DatagramPacket dp) {
		final byte[] data = dp.getData();
		final int offset = dp.getOffset();
		final int length = dp.getLength();
		
		final String candidateKey;
		if (length >= RTP_HEADER_LENGTH && RtpVersion.VER2.equals(RtpUtil.getVersion(data[offset]))) {
			candidateKey = "VOIP_" + RtpUtil.getSsrc(Arrays.copyOfRange(data, offset, offset + RTP_HEADER_LENGTH));
		}
		else if (length >= UDP_PAYLOAD_HEADER_LENGTH) {
			candidateKey = new String(data, offset + 2, 36, StandardCharsets.ISO_8859_1);
		}
		else {
			return;
		}
		
		final UdpTestCandidate clientData = incomingMap.get(candidateKey);
		if (clientData == null || clientData.isError() || clientData.getOnUdpPacketReceivedCallback() == null) {
			return;
		}
		
		TestServerConsole.log("received UDP from: " + dp.getAddress() + ":" + dp.getPort() + " (on local port :" + getLocalPort() 
				+ "), candidate: " + LineReader.sanitize(candidateKey), 1, TestServerServiceEnum.UDP_SERVICE);
		
		//run the callback provided by the clienthandler in the background:
		TestServer.getInstance().getCommonThreadPool().submit(() -> 
			clientData.getOnUdpPacketReceivedCallback().onReceive(dp, candidateKey, this));
	}
	
	public abstract T getSocket();
	
	/**
	 * 
	 * @param dp
	 * @throws IOException 
	 */
	public abstract void send(DatagramPacket dp) throws IOException;
	
	/**
	 * 
	 * @return
	 */
	public abstract boolean getIsRunning();
	
	/**
	 * 
	 * @return
	 */
	public abstract InetAddress getAddress();
	
	/**
	 * 
	 * @return
	 */
	public abstract int getLocalPort();	
}
