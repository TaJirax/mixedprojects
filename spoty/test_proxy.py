import socket
import socketserver
import threading
import unittest

from spotify_downloader import Socks5HttpBridge, normalize_proxy_url


def recv_exact(sock, size):
    data = bytearray()
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            raise ConnectionError("unexpected EOF")
        data.extend(chunk)
    return bytes(data)


class ThreadingServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


class EchoHandler(socketserver.BaseRequestHandler):
    def handle(self):
        while True:
            data = self.request.recv(4096)
            if not data:
                return
            self.request.sendall(data)


class SocksHandler(socketserver.BaseRequestHandler):
    def handle(self):
        version, count = recv_exact(self.request, 2)
        self.assert_byte(version, 5)
        recv_exact(self.request, count)
        self.request.sendall(b"\x05\x00")

        version, command, _, address_type = recv_exact(self.request, 4)
        self.assert_byte(version, 5)
        self.assert_byte(command, 1)
        if address_type == 1:
            host = socket.inet_ntoa(recv_exact(self.request, 4))
        elif address_type == 3:
            host = recv_exact(self.request, recv_exact(self.request, 1)[0]).decode("idna")
        else:
            raise AssertionError("unexpected address type")
        port = int.from_bytes(recv_exact(self.request, 2), "big")
        upstream = socket.create_connection((host, port), timeout=5)
        self.request.sendall(b"\x05\x00\x00\x01\x00\x00\x00\x00\x00\x00")
        try:
            Socks5HttpBridge._relay(self.request, upstream)
        finally:
            upstream.close()

    @staticmethod
    def assert_byte(actual, expected):
        if actual != expected:
            raise AssertionError(f"expected {expected}, got {actual}")


class ProxyTests(unittest.TestCase):
    def setUp(self):
        self.echo = ThreadingServer(("127.0.0.1", 0), EchoHandler)
        self.socks = ThreadingServer(("127.0.0.1", 0), SocksHandler)
        self.echo_thread = threading.Thread(target=self.echo.serve_forever, daemon=True)
        self.socks_thread = threading.Thread(target=self.socks.serve_forever, daemon=True)
        self.echo_thread.start()
        self.socks_thread.start()
        socks_url = f"socks5://127.0.0.1:{self.socks.server_address[1]}"
        self.bridge = Socks5HttpBridge(socks_url)
        self.bridge_url = self.bridge.start()

    def tearDown(self):
        self.bridge.close()
        self.socks.shutdown()
        self.socks.server_close()
        self.echo.shutdown()
        self.echo.server_close()

    def test_normalize_proxy_url(self):
        self.assertEqual(normalize_proxy_url("127.0.0.1:10809", "http"), "http://127.0.0.1:10809")
        self.assertEqual(
            normalize_proxy_url("socks5://user:p%40ss@localhost:10808"),
            "socks5://user:p%40ss@localhost:10808",
        )

    def test_connect_tunnel_through_socks(self):
        bridge = socket.create_connection(url_address(self.bridge_url), timeout=5)
        target_port = self.echo.server_address[1]
        bridge.sendall(f"CONNECT 127.0.0.1:{target_port} HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".encode())
        response = recv_until(bridge, b"\r\n\r\n")
        self.assertIn(b"200 Connection Established", response)
        bridge.sendall(b"bridge works")
        self.assertEqual(recv_exact(bridge, 12), b"bridge works")
        bridge.close()


def url_address(url):
    from urllib.parse import urlsplit

    parsed = urlsplit(url)
    return parsed.hostname, parsed.port


def recv_until(sock, marker):
    data = bytearray()
    while marker not in data:
        data.extend(sock.recv(4096))
    return bytes(data)


if __name__ == "__main__":
    unittest.main()
