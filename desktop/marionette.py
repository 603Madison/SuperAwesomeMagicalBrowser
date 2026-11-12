"""A small Marionette client (Firefox's remote protocol), enough for check.py."""
import base64
import json
import socket
import time


class Marionette:
    def __init__(self, port=2828, host="127.0.0.1", timeout=90):
        deadline = time.time() + timeout
        while True:
            try:
                self.sock = socket.create_connection((host, port), timeout=60)
                break
            except OSError:
                if time.time() > deadline:
                    raise RuntimeError("Marionette did not start")
                time.sleep(0.5)
        self.buf = b""
        self.msg_id = 0
        self._read()  # the server's hello
        self.command("WebDriver:NewSession", {"capabilities": {}})
        self.command("Marionette:SetContext", {"value": "chrome"})

    def _read(self):
        while b":" not in self.buf:
            self.buf += self._recv()
        size, rest = self.buf.split(b":", 1)
        size = int(size)
        while len(rest) < size:
            rest += self._recv()
        self.buf = rest[size:]
        return json.loads(rest[:size])

    def _recv(self):
        data = self.sock.recv(1 << 20)
        if not data:
            raise RuntimeError("Marionette closed the connection")
        return data

    def command(self, name, params=None):
        self.msg_id += 1
        packet = json.dumps([0, self.msg_id, name, params or {}]).encode()
        self.sock.sendall(str(len(packet)).encode() + b":" + packet)
        while True:
            reply = self._read()
            if reply[0] == 1 and reply[1] == self.msg_id:
                if reply[2]:
                    raise RuntimeError(f"{name}: {reply[2].get('message', reply[2])}")
                return reply[3]

    def js(self, script, *args):
        """Runs an async function body in the browser window and returns its result."""
        wrapped = "return (async () => {\n" + script + "\n})();"
        return self.command("WebDriver:ExecuteScript", {"script": wrapped, "args": list(args)}).get("value")

    def screenshot(self, path):
        data = self.command("WebDriver:TakeScreenshot", {"full": False, "hash": False})["value"]
        with open(path, "wb") as f:
            f.write(base64.b64decode(data))

    def quit(self):
        try:
            self.command("Marionette:Quit", {"flags": ["eForceQuit"]})
        except Exception:
            pass
