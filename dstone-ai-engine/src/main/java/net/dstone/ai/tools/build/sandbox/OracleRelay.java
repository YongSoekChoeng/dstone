package net.dstone.ai.tools.build.sandbox;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 앱과 시험 전용 Oracle 사이의 중계입니다(127.0.0.1에서만 받음).
 *
 * 주소만 돌려서는 안 됩니다. Oracle은 접속할 때 서비스 이름을 같이 보내는데, 앱은 원래 DB의 이름을 보내기 때문입니다.
 * 그래서 접속 첫 패킷(CONNECT)의 서비스 이름·SID를 이번 실행의 PDB로 바꿉니다.
 * 무엇을 보내든 바꾸므로, 이 문으로는 시험 서버의 다른 PDB나 CDB에 들어갈 수 없습니다.
 *
 * 다른 주소로 다시 접속하라는 답(REDIRECT)은 넘기지 않고 끊습니다. 중계를 벗어나기 때문입니다.
 * </pre>
 */
final class OracleRelay implements Closeable {

	private static final int HEADER = 8;
	private static final int CONNECT = 1;
	private static final int ACCEPT = 2;
	private static final int REFUSE = 4;
	private static final int REDIRECT = 5;
	private static final int DATA = 6;
	private static final int RESEND = 11;

	/** CONNECT 패킷에 접속 문자열을 같이 실을 수 있는 길이. 넘으면 다음 DATA 패킷에 싣는다 */
	private static final int INLINE_LIMIT = 230;
	/** 접속을 맺기 전의 패킷 크기 한도 */
	private static final int FIRST_PACKET_LIMIT = 255;
	private static final int DATA_LENGTH_AT = 24;
	private static final int DATA_OFFSET_AT = 26;
	private static final int TIMEOUT_MILLIS = 20000;
	private static final int MAX_EXCHANGES = 8;

	private static final Pattern TARGET = Pattern.compile("\\(\\s*(SERVICE_NAME|SID|INSTANCE_NAME|SERVER)\\s*=[^()]*\\)", Pattern.CASE_INSENSITIVE);
	private static final Pattern CONNECT_DATA = Pattern.compile("\\(\\s*CONNECT_DATA\\s*=", Pattern.CASE_INSENSITIVE);

	private final ServerSocket listener;
	private final InetSocketAddress server;
	private final String service;
	private final Set<Socket> open = ConcurrentHashMap.newKeySet();

	/**
	 * @param server  시험 전용 Oracle의 주소
	 * @param service 이번 실행의 PDB 서비스 이름
	 */
	OracleRelay(SandboxGuard.Endpoint server, String service) throws IOException {
		this.server = new InetSocketAddress(server.host(), server.port());
		this.service = service;
		this.listener = new ServerSocket(0, 100, InetAddress.getByName("127.0.0.1"));
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				OracleRelay.this.acceptLoop();
			}
		}, "dstone-sandbox-oracle-" + this.listener.getLocalPort());
		thread.setDaemon(true);
		thread.start();
	}

	int port() {
		return this.listener.getLocalPort();
	}

	private void acceptLoop() {
		while (!this.listener.isClosed()) {
			try {
				final Socket client = this.listener.accept();
				Thread thread = new Thread(new Runnable() {
					@Override
					public void run() {
						OracleRelay.this.serve(client);
					}
				}, "dstone-sandbox-oracle-conn");
				thread.setDaemon(true);
				thread.start();
			} catch (IOException e) {
				if (this.listener.isClosed()) {
					return;
				}
			}
		}
	}

	private void serve(final Socket client) {
		this.open.add(client);
		final Socket target = new Socket(Proxy.NO_PROXY);
		this.open.add(target);
		try {
			client.setSoTimeout(TIMEOUT_MILLIS);
			target.connect(this.server, TIMEOUT_MILLIS);
			target.setSoTimeout(TIMEOUT_MILLIS);
			client.setTcpNoDelay(true);
			target.setTcpNoDelay(true);
			if (!this.handshake(client, target)) {
				return;
			}
			client.setSoTimeout(0);
			target.setSoTimeout(0);
			Thread back = new Thread(new Runnable() {
				@Override
				public void run() {
					pump(target, client);
				}
			}, "dstone-sandbox-oracle-pump");
			back.setDaemon(true);
			back.start();
			pump(client, target);
			back.join();
		} catch (IOException e) {
			// 어느 한쪽이 끊었다. 앱에는 접속 오류로 보인다
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			this.release(client);
			this.release(target);
		}
	}

	/** ACCEPT까지 이어 줍니다. 그 뒤의 글은 들여다보지 않습니다. */
	private boolean handshake(Socket client, Socket target) throws IOException {
		DataInputStream fromClient = new DataInputStream(client.getInputStream());
		DataInputStream fromServer = new DataInputStream(target.getInputStream());
		OutputStream toClient = client.getOutputStream();
		OutputStream toServer = target.getOutputStream();
		for (int i = 0; i < MAX_EXCHANGES; i++) {
			byte[] connect = packet(fromClient);
			if (connect[4] != CONNECT || connect.length < DATA_OFFSET_AT + 2) {
				return false; // TLS이거나 Oracle 접속이 아니다
			}
			int length = number(connect, DATA_LENGTH_AT);
			int offset = number(connect, DATA_OFFSET_AT);
			if (length <= 0 || offset < DATA_OFFSET_AT + 2 || offset > connect.length) {
				return false;
			}
			byte[] text;
			if (offset + length <= connect.length) {
				text = new byte[length];
				System.arraycopy(connect, offset, text, 0, length);
			} else {
				// javaNetNio=false인 드라이버는 긴 접속 문자열을 255바이트 DATA 패킷 여러 개로 나눠 보낸다
				text = new byte[length];
				int filled = 0;
				while (filled < length) {
					byte[] data = packet(fromClient);
					int size = data.length - HEADER - 2;
					if (data[4] != DATA || size <= 0 || size > length - filled) {
						return false;
					}
					System.arraycopy(data, HEADER + 2, text, filled, size);
					filled += size;
				}
			}
			String changed = retarget(new String(text, StandardCharsets.ISO_8859_1), this.service);
			if (changed == null) {
				return false;
			}
			toServer.write(connectPacket(connect, offset, changed.getBytes(StandardCharsets.ISO_8859_1)));
			toServer.flush();

			byte[] answer = packet(fromServer);
			if (answer[4] == REDIRECT) {
				return false;
			}
			toClient.write(answer);
			toClient.flush();
			if (answer[4] == ACCEPT || answer[4] == REFUSE) {
				return true; // 거절이면 이유가 뒤따르고 서버가 끊는다
			}
			if (answer[4] != RESEND) {
				return false;
			}
		}
		return false;
	}

	/**
	 * 접속 문자열의 목적지를 바꿉니다.
	 *
	 * @param text    앱이 보낸 접속 문자열
	 * @param service 이번 실행의 서비스 이름
	 * @return 바꾼 것. CONNECT_DATA가 없으면 null
	 */
	static String retarget(String text, String service) {
		String stripped = TARGET.matcher(text).replaceAll("");
		Matcher m = CONNECT_DATA.matcher(stripped);
		if (!m.find()) {
			return null;
		}
		return stripped.substring(0, m.end()) + "(SERVICE_NAME=" + service + ")" + stripped.substring(m.end());
	}

	/** 앱의 CONNECT 머리를 그대로 두고 접속 문자열과 길이만 바꾼 패킷입니다(넘치면 DATA 패킷이 붙음). */
	private static byte[] connectPacket(byte[] original, int offset, byte[] text) {
		boolean inline = text.length <= INLINE_LIMIT;
		int head = inline ? offset + text.length : offset;
		int chunk = FIRST_PACKET_LIMIT - HEADER - 2;
		int packets = inline ? 0 : (text.length + chunk - 1) / chunk;
		byte[] out = new byte[head + (inline ? 0 : packets * (HEADER + 2) + text.length)];
		System.arraycopy(original, 0, out, 0, offset);
		write(out, 0, head);
		write(out, DATA_LENGTH_AT, text.length);
		if (inline) {
			System.arraycopy(text, 0, out, offset, text.length);
		} else {
			int at = head;
			for (int from = 0; from < text.length; from += chunk) {
				int size = Math.min(chunk, text.length - from);
				write(out, at, HEADER + 2 + size);
				out[at + 4] = DATA;
				System.arraycopy(text, from, out, at + HEADER + 2, size);
				at += HEADER + 2 + size;
			}
		}
		return out;
	}

	private static byte[] packet(DataInputStream in) throws IOException {
		byte[] head = new byte[HEADER];
		in.readFully(head);
		int length = number(head, 0);
		if (length < HEADER) {
			throw new IOException("Oracle 접속 패킷이 아닙니다");
		}
		byte[] all = new byte[length];
		System.arraycopy(head, 0, all, 0, HEADER);
		in.readFully(all, HEADER, length - HEADER);
		return all;
	}

	private static int number(byte[] bytes, int at) {
		return ((bytes[at] & 0xff) << 8) | (bytes[at + 1] & 0xff);
	}

	private static void write(byte[] bytes, int at, int value) {
		bytes[at] = (byte) (value >> 8);
		bytes[at + 1] = (byte) value;
	}

	private static void pump(Socket from, Socket to) {
		byte[] buffer = new byte[32768];
		try {
			InputStream in = from.getInputStream();
			OutputStream out = to.getOutputStream();
			int count;
			while ((count = in.read(buffer)) >= 0) {
				out.write(buffer, 0, count);
				out.flush();
			}
			to.shutdownOutput();
		} catch (IOException e) {
			// 한쪽이 끊기면 다른 쪽도 끊는다
			closeQuietly(from);
			closeQuietly(to);
		}
	}

	private void release(Socket socket) {
		this.open.remove(socket);
		closeQuietly(socket);
	}

	@Override
	public void close() {
		closeQuietly(this.listener);
		for (Socket socket : this.open) {
			closeQuietly(socket);
		}
		this.open.clear();
	}

	private static void closeQuietly(Closeable closeable) {
		try {
			closeable.close();
		} catch (IOException e) {
			// 닫다가 난 오류는 상관없다
		}
	}

}
