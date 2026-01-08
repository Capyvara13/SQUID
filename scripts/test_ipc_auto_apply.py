import subprocess, time, json, sys

JAVA_CMD = 'mvn -f java-backend exec:java -Dexec.mainClass=com.squid.core.ipc.IPCMain'

def start_java():
    print('Starting Java IPC...')
    proc = subprocess.Popen(JAVA_CMD, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
    return proc


def send_and_wait(proc, msg, timeout=20):
    s = json.dumps(msg)
    proc.stdin.write(s + '\n')
    proc.stdin.flush()

    deadline = time.time() + timeout
    while time.time() < deadline:
        line = proc.stdout.readline()
        if not line:
            time.sleep(0.1)
            continue
        line = line.strip()
        try:
            obj = json.loads(line)
            return obj
        except Exception:
            # ignore non-json lines
            continue
    raise TimeoutError('No JSON response within timeout')


def main():
    proc = start_java()
    try:
        time.sleep(2)
        payload = {
            'params': {'b': 4, 'm': 3, 't': 128},
            'features': [
                {
                    'depth': 3,
                    'index': 0,
                    'index_hash': 0,
                    'local_entropy': 7.8,
                    'timestamp': int(time.time() * 1000),
                    'global_L': 64,
                    'global_b': 4,
                    'global_m': 3,
                    'global_t': 128,
                    'last_access_count': 0,
                    'leaf_hist_score': 0.5
                }
            ]
        }
        msg = {'cmd': 'decide', 'payload': payload, 'auto_apply': True}
        print('Sending decide with auto_apply:true')
        resp = send_and_wait(proc, msg, timeout=30)
        print('Response:', json.dumps(resp, indent=2))
        if 'applied_rotation' in resp:
            print('Test passed: applied_rotation present')
            sys.exit(0)
        else:
            print('Test failed: applied_rotation not present')
            sys.exit(2)
    except Exception as e:
        print('Test error:', e)
        sys.exit(3)
    finally:
        try:
            proc.terminate()
        except Exception:
            pass

if __name__ == '__main__':
    main()
