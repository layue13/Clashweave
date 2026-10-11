"""FIFO wall-clock TCP delay. perf_counter avoids Windows' coarse asyncio clock."""
import argparse
import queue
import socket
import threading
import time

parser = argparse.ArgumentParser()
parser.add_argument('--half-ms', type=float, required=True)
args = parser.parse_args()
delay = args.half_ms / 1000


def direction(source, destination):
    packets = queue.Queue(maxsize=512)

    def read():
        try:
            while True:
                payload = source.recv(65536)
                if not payload: break
                packets.put((time.perf_counter()+delay,payload))
        except OSError:
            pass
        finally:
            packets.put((0,None))

    threading.Thread(target=read,daemon=True).start()
    try:
        while True:
            due,payload=packets.get()
            if payload is None: break
            while True:
                remaining=due-time.perf_counter()
                if remaining<=0: break
                time.sleep(remaining)
            destination.sendall(payload)
    except OSError:
        pass
    finally:
        try: destination.shutdown(socket.SHUT_WR)
        except OSError: pass


listener=socket.socket()
listener.bind(('127.0.0.1',25581))
listener.listen(4)
print('LATENCY_READY half_ms='+str(args.half_ms),flush=True)
while True:
    downstream,_=listener.accept()
    upstream=socket.create_connection(('127.0.0.1',25580))
    for source,destination in [(downstream,upstream),(upstream,downstream)]:
        threading.Thread(target=direction,args=(source,destination),daemon=True).start()
