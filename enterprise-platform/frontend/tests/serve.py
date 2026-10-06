from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
import os
os.chdir(Path(__file__).resolve().parents[1])
class Handler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/":
            self.path = "/task-management-ui/index.html"
        super().do_GET()
    def log_message(self, *args):
        pass
ThreadingHTTPServer(("127.0.0.1",8123),Handler).serve_forever()
