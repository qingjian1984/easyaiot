import sys
from pathlib import Path

NODE_ROOT = Path(__file__).resolve().parents[1]
if str(NODE_ROOT) not in sys.path:
    sys.path.insert(0, str(NODE_ROOT))
