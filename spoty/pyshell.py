"""One place that starts a tool.

On a desktop this is `subprocess` with the console window suppressed, and
nothing else. On Android there is no second Python to spawn — Chaquopy embeds
the interpreter inside the app process and ships no standalone binary — so a
tool that is an importable package is run on a thread here instead, writing
into a pipe the caller reads exactly as it reads a real process's stdout.

The engine therefore keeps one way of running yt-dlp: build an argv, read
lines, parse them. Whether those lines crossed a process boundary is this
module's business and no one else's.
"""

import io
import os
import subprocess
import sys
import threading

from blueknight_paths import IS_ANDROID, IS_WINDOWS

# Suppressing the console window is a Windows concept; everywhere else the
# flag does not exist and the empty mapping adds nothing to the call.
NO_WINDOW = ({"creationflags": subprocess.CREATE_NO_WINDOW}
             if IS_WINDOWS and hasattr(subprocess, "CREATE_NO_WINDOW") else {})

# argv[0] stem -> the module whose main() accepts the rest of the argv. Only
# consulted when the named program is not on disk, which is the Android case.
IN_PROCESS = {
    "yt-dlp": ("yt_dlp", "main"),
    "gallery-dl": ("gallery_dl", "main"),
    "streamlink": ("streamlink_cli.main", "main"),
}


def _entry_point(name):
    """Import and return the main() for a tool, or None if it is not installed."""
    target = IN_PROCESS.get(os.path.splitext(os.path.basename(str(name)))[0])
    if not target:
        return None
    module_name, attribute = target
    try:
        module = __import__(module_name, fromlist=["*"])
        return getattr(module, attribute)
    except Exception:
        return None


def _in_process(cmd):
    """True when cmd names a tool we must run inside this interpreter."""
    if not IS_ANDROID or not cmd:
        return False
    return not os.path.isfile(str(cmd[0])) and _entry_point(cmd[0]) is not None


def module_available(name):
    """Return whether an embedded tool's actual command entry point imports.

    Availability must not be inferred from ``__version__``: yt-dlp keeps its
    version in a submodule on some releases even though ``yt_dlp.main`` is
    present and fully usable.
    """
    return _entry_point(name) is not None


class _Embedded:
    """A Popen stand-in that runs a Python entry point on a thread.

    Only the surface the engine actually uses is implemented: stdout, wait(),
    returncode and terminate(). Stopping works by closing the read end of the
    pipe — the tool's next write fails, which unwinds it the same way a killed
    process dies, without needing a cooperative cancel hook it does not offer.
    """

    def __init__(self, cmd, env=None):
        self.args = list(cmd)
        self.returncode = None
        self._env = env
        read_fd, write_fd = os.pipe()
        self.stdout = io.TextIOWrapper(
            open(read_fd, "rb", buffering=0), encoding="utf-8", errors="replace")
        self._sink = io.TextIOWrapper(
            open(write_fd, "wb", buffering=0), encoding="utf-8",
            errors="replace", write_through=True)
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def _run(self):
        entry = _entry_point(self.args[0])
        # ponytail: sys.stdout is process-global, so this relies on the engine's
        # existing one-job-at-a-time guard. Give each tool its own interpreter
        # context only if concurrent downloads are ever added.
        saved_out, saved_err, saved_argv = sys.stdout, sys.stderr, sys.argv
        saved_env = dict(os.environ)
        try:
            if self._env is not None:
                os.environ.clear()
                os.environ.update(self._env)
            sys.stdout = sys.stderr = self._sink
            sys.argv = list(self.args)
            try:
                entry(self.args[1:])
                self.returncode = 0
            except SystemExit as exit_request:
                self.returncode = int(exit_request.code or 0)
        except (BrokenPipeError, ValueError, OSError):
            # The reader closed the pipe: this is our stop button, not a fault.
            self.returncode = self.returncode if self.returncode is not None else 1
        except Exception as failure:
            self.returncode = 1
            with _quiet():
                saved_out.write(f"ERROR: {failure}\n")
        finally:
            sys.stdout, sys.stderr, sys.argv = saved_out, saved_err, saved_argv
            os.environ.clear()
            os.environ.update(saved_env)
            with _quiet():
                self._sink.close()

    def wait(self, timeout=None):
        self._thread.join(timeout)
        return self.returncode

    def poll(self):
        return None if self._thread.is_alive() else self.returncode

    def terminate(self):
        with _quiet():
            self.stdout.close()

    kill = terminate


class _quiet:
    """Swallow the errors that only happen because we are already tearing down."""

    def __enter__(self):
        return self

    def __exit__(self, kind, value, trace):
        return kind is not None and issubclass(kind, (OSError, ValueError))


def popen(cmd, **kwargs):
    """Start a tool and return something with .stdout, .wait() and .terminate()."""
    if _in_process(cmd):
        return _Embedded(cmd, env=kwargs.get("env"))
    kwargs.setdefault("stdout", subprocess.PIPE)
    kwargs.setdefault("stderr", subprocess.STDOUT)
    kwargs.setdefault("text", True)
    kwargs.setdefault("encoding", "utf-8")
    kwargs.setdefault("errors", "replace")
    return subprocess.Popen(cmd, **{**NO_WINDOW, **kwargs})


def run(cmd, **kwargs):
    """Run a tool to completion. Mirrors subprocess.run's return shape."""
    if _in_process(cmd):
        child = _Embedded(cmd, env=kwargs.get("env"))
        output = child.stdout.read()
        child.wait(kwargs.get("timeout"))
        return subprocess.CompletedProcess(list(cmd), child.returncode or 0, output, "")
    kwargs.setdefault("stdout", subprocess.PIPE)
    kwargs.setdefault("stderr", subprocess.STDOUT)
    kwargs.setdefault("text", True)
    kwargs.setdefault("encoding", "utf-8")
    kwargs.setdefault("errors", "replace")
    return subprocess.run(cmd, **{**NO_WINDOW, **kwargs})


def module_version(name):
    """The version a tool reports when it is imported rather than launched."""
    target = IN_PROCESS.get(os.path.splitext(os.path.basename(str(name)))[0])
    if not target:
        return "unknown"
    try:
        module = __import__(target[0].split(".")[0], fromlist=["*"])
        return str(getattr(module, "__version__", "unknown"))
    except Exception:
        return "unknown"


def demo():
    """Self-check: the shim must behave like subprocess on this machine."""
    result = run([sys.executable, "-c", "print('hello'); raise SystemExit(3)"])
    assert result.returncode == 3, result.returncode
    assert "hello" in result.stdout, result.stdout

    child = popen([sys.executable, "-c",
                   "import sys\nfor i in range(3): print(i); sys.stdout.flush()"])
    assert [line.strip() for line in child.stdout] == ["0", "1", "2"]
    assert child.wait() == 0

    # A tool with no importable package must never be claimed by the shim,
    # on any platform — otherwise a missing binary would look like a hang.
    assert not _in_process(["definitely-not-a-tool"])
    assert _entry_point("ffmpeg") is None
    print("pyshell: ok")


if __name__ == "__main__":
    demo()
