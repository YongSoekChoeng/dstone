package net.dstone.ai.tools.build.sandbox;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * <pre>
 * 시험용 앱이 밖으로 여는 연결을 모두 받아 보는 문입니다(SOCKS5 대리 서버, 127.0.0.1에서만 받음).
 *
 *   원래 DB로 가는 연결   → 일회용 DB로 돌린다
 *   허용한 곳으로 가는 연결 → 그대로 잇는다(앱 자신의 포트, 설정 sandbox-allow-hosts)
 *   그 밖의 연결          → 끊는다(단, 빌드할 때는 라이브러리를 받아야 하므로 잇는다)
 *
 * 앱의 설정 파일에서 접속 주소를 찾아 바꾸지 않습니다. 주소는 환경변수·코드·JNDI 어디에나 있을 수 있어서 하나만 놓쳐도 원래 DB에 쓰기 때문입니다.
 * 대신 JVM 옵션(jvmOptions())으로 새 소켓을 전부 이리로 보냅니다. JDK 8·11·21·24(Linux, Windows)에서 localhost까지 잡히는 것을 확인했습니다.
 *
 * Oracle thin 드라이버는 12c~19c가 NIO로 접속합니다. oracle.jdbc.javaNetNio=false를 주면 일반 소켓을 써서 잡힙니다
 * (11g, 21c, 23ai는 주지 않아도 잡힘. 11.2~23.26, JDK 8·11·21·24에서 확인). 앱이 그 값을 되돌리면 우회되므로 SandboxArtifact가 막습니다.
 * oracle.net.disableOob는 중계가 TCP 긴급 데이터를 옮기지 못해서 끕니다.
 *
 * 잡지 못하는 것: NIO SocketChannel로 여는 연결(SQL Server 드라이버, Netty 계열).
 * 그래서 그런 것을 쓰는 프로젝트는 격리 시험 서버를 띄우지 않습니다(TestSandbox).
 * </pre>
 */
public final class SandboxGuard implements Closeable {

	private static final int VERSION = 5;
	private static final int TYPE_IPV4 = 1;
	private static final int TYPE_DOMAIN = 3;
	private static final int TYPE_IPV6 = 4;
	private static final int REPLY_OK = 0;
	private static final int REPLY_DENIED = 2;
	private static final int REPLY_FAILED = 4;

	/** 연결을 잇거나 첫 인사를 기다리는 시간 */
	private static final int TIMEOUT_MILLIS = 15000;

	/**
	 * 주소 하나(호스트와 포트)입니다.
	 *
	 * @param host 호스트 이름이나 IP
	 * @param port 포트. 0이면 모든 포트(허용 목록에서만)
	 */
	public record Endpoint(String host, int port) {
	}

	private final ServerSocket listener;
	private final List<Endpoint> redirected;
	private final int redirectPort;
	private final List<Endpoint> allowed = new CopyOnWriteArrayList<Endpoint>();
	private final boolean allowOthers;
	private final Set<Socket> open = ConcurrentHashMap.newKeySet();
	private final Set<String> denied = new ConcurrentSkipListSet<String>();
	/** 이어 주되 수를 세는 곳(테스트 서버의 개발 DB). 테스트 드라이버를 거치지 않은 연결을 찾으려고 셉니다 */
	private final List<Endpoint> watched = new CopyOnWriteArrayList<Endpoint>();
	private final java.util.concurrent.atomic.AtomicInteger watchedCount = new java.util.concurrent.atomic.AtomicInteger();

	/**
	 * @param redirected   일회용 DB로 돌릴 원래 DB 주소들
	 * @param redirectPort 일회용 DB의 포트(127.0.0.1)
	 * @param allowOthers  true면 그 밖의 연결도 잇는다(빌드용)
	 */
	public SandboxGuard(List<Endpoint> redirected, int redirectPort, boolean allowOthers) throws IOException {
		this.redirected = new ArrayList<Endpoint>(redirected);
		this.redirectPort = redirectPort;
		this.allowOthers = allowOthers;
		this.listener = new ServerSocket(0, 100, InetAddress.getByName("127.0.0.1"));
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				SandboxGuard.this.acceptLoop();
			}
		}, "dstone-sandbox-guard-" + this.listener.getLocalPort());
		thread.setDaemon(true);
		thread.start();
	}

	/** 그대로 이어 줄 곳을 더합니다. */
	public void allow(Endpoint endpoint) {
		this.allowed.add(endpoint);
	}

	/** 그대로 이어 주되 연결 수를 셀 곳을 더합니다. */
	public void watch(Endpoint endpoint) {
		this.watched.add(endpoint);
	}

	/** watch한 곳으로 이은 연결 수(지금까지 합계)입니다. */
	public int watchedCount() {
		return this.watchedCount.get();
	}

	/** 이 문을 지나게 하는 JVM 옵션입니다. socksNonProxyHosts를 비워야 localhost도 지나갑니다. */
	public String jvmOptions() {
		return "-DsocksProxyHost=127.0.0.1 -DsocksProxyPort=" + this.listener.getLocalPort() + " -DsocksProxyVersion=5 -DsocksNonProxyHosts="
			+ " -Doracle.jdbc.javaNetNio=false -Doracle.net.disableOob=true";
	}

	/** 막은 연결(호스트:포트)들입니다. */
	public List<String> denied() {
		return new ArrayList<String>(this.denied);
	}

	private void acceptLoop() {
		while (!this.listener.isClosed()) {
			try {
				final Socket client = this.listener.accept();
				Thread thread = new Thread(new Runnable() {
					@Override
					public void run() {
						SandboxGuard.this.serve(client);
					}
				}, "dstone-sandbox-conn");
				thread.setDaemon(true);
				thread.start();
			} catch (IOException e) {
				if (this.listener.isClosed()) {
					return;
				}
			}
		}
	}

	private void serve(Socket client) {
		this.open.add(client);
		Socket target = null;
		try {
			client.setSoTimeout(TIMEOUT_MILLIS);
			DataInputStream in = new DataInputStream(client.getInputStream());
			OutputStream out = client.getOutputStream();
			if (in.readUnsignedByte() != VERSION) {
				return;
			}
			in.readFully(new byte[in.readUnsignedByte()]); // 인증 방법 목록. 인증 없음으로 답한다
			out.write(new byte[] { VERSION, 0 });
			in.readUnsignedByte(); // version
			int command = in.readUnsignedByte();
			in.readUnsignedByte(); // reserved
			String host = readHost(in);
			int port = in.readUnsignedShort();
			if (command != 1 || host == null) {
				reply(out, REPLY_DENIED);
				return;
			}
			InetSocketAddress destination = this.destinationOf(host, port);
			if (destination == null) {
				this.denied.add(host + ":" + port);
				reply(out, REPLY_DENIED);
				return;
			}
			target = new Socket(Proxy.NO_PROXY);
			this.open.add(target);
			try {
				target.connect(destination, TIMEOUT_MILLIS);
			} catch (IOException e) {
				reply(out, REPLY_FAILED);
				return;
			}
			reply(out, REPLY_OK);
			client.setSoTimeout(0);
			client.setTcpNoDelay(true);
			target.setTcpNoDelay(true);
			final Socket from = target;
			final Socket to = client;
			Thread back = new Thread(new Runnable() {
				@Override
				public void run() {
					pump(from, to);
				}
			}, "dstone-sandbox-pump");
			back.setDaemon(true);
			back.start();
			pump(client, target);
			back.join();
		} catch (IOException e) {
			// 앱이 연결을 끊었다. 할 일이 없다
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			this.release(client);
			this.release(target);
		}
	}

	/** 실제로 이을 곳을 정합니다. 끊을 연결이면 null. */
	private InetSocketAddress destinationOf(String host, int port) {
		for (Endpoint endpoint : this.redirected) {
			if (endpoint.port() == port && sameHost(endpoint.host(), host)) {
				return new InetSocketAddress("127.0.0.1", this.redirectPort);
			}
		}
		for (Endpoint endpoint : this.watched) {
			if (endpoint.port() == port && sameHost(endpoint.host(), host)) {
				this.watchedCount.incrementAndGet();
				return new InetSocketAddress(host, port);
			}
		}
		if (this.allowOthers) {
			return new InetSocketAddress(host, port);
		}
		for (Endpoint endpoint : this.allowed) {
			if ((endpoint.port() == 0 || endpoint.port() == port) && sameHost(endpoint.host(), host)) {
				return new InetSocketAddress(host, port);
			}
		}
		return null;
	}

	/** 두 이름이 같은 컴퓨터를 가리키는가. 이 컴퓨터를 가리키는 이름(localhost, 127.x, ::1, LAN IP)은 모두 같다고 봅니다. */
	public static boolean sameHost(String expected, String actual) {
		if (expected.equalsIgnoreCase(actual)) {
			return true;
		}
		try {
			InetAddress[] left = InetAddress.getAllByName(expected);
			InetAddress[] right = InetAddress.getAllByName(actual);
			for (InetAddress a : left) {
				for (InetAddress b : right) {
					if (a.equals(b) || (isLocal(a) && isLocal(b))) {
						return true;
					}
				}
			}
		} catch (UnknownHostException | SocketException e) {
			return false;
		}
		return false;
	}

	private static boolean isLocal(InetAddress address) throws SocketException {
		return address.isLoopbackAddress() || address.isAnyLocalAddress() || NetworkInterface.getByInetAddress(address) != null;
	}

	private static String readHost(DataInputStream in) throws IOException {
		int type = in.readUnsignedByte();
		int length;
		if (type == TYPE_IPV4) {
			length = 4;
		} else if (type == TYPE_IPV6) {
			length = 16;
		} else if (type == TYPE_DOMAIN) {
			length = in.readUnsignedByte();
		} else {
			return null;
		}
		byte[] raw = new byte[length];
		in.readFully(raw);
		return type == TYPE_DOMAIN ? new String(raw, StandardCharsets.US_ASCII) : InetAddress.getByAddress(raw).getHostAddress();
	}

	private static void reply(OutputStream out, int code) throws IOException {
		out.write(new byte[] { VERSION, (byte) code, 0, TYPE_IPV4, 0, 0, 0, 0, 0, 0 });
		out.flush();
	}

	private static void pump(Socket from, Socket to) {
		byte[] buffer = new byte[16384];
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
		if (socket != null) {
			this.open.remove(socket);
			closeQuietly(socket);
		}
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
