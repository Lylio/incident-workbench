"""First-launch port selection. Never overwrites existing .env or API keys."""
from pathlib import Path
import socket
ROOT = Path(__file__).resolve().parent.parent

def available(candidate, reserved):
    for port in range(candidate, candidate + 1000):
        if port in reserved:
            continue
        try:
            with socket.socket() as probe:
                probe.bind(('127.0.0.1', port))
            return port
        except OSError:
            pass
    raise RuntimeError(f'No available port near {candidate}')

def main():
    target = ROOT / '.env'
    if target.exists():
        print('Using existing .env settings.')
        return
    chosen, lines = set(), []
    for key, default in [('WORKBENCH_UI_PORT', 53493), ('WORKBENCH_API_PORT', 58493), ('WORKBENCH_DB_PORT', 55493)]:
        port = available(default, chosen)
        chosen.add(port)
        lines.append(f'{key}={port}')
    lines += ['OPENAI_API_KEY=', 'OPENAI_MODEL=gpt-4.1-mini']
    try:
        with target.open('x') as out:
            out.write('\n'.join(lines) + '\n')
    except FileExistsError:
        print('Another launch already created .env; preserving it.')
        return
    print('Selected host ports: ' + ', '.join(lines[:3]))

if __name__ == '__main__':
    main()
