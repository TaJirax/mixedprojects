"""Point Tkinter at Tcl/Tk data explicitly in frozen one-file builds."""

import os
import sys
from pathlib import Path


bundle_root = getattr(sys, "_MEIPASS", None)
if bundle_root:
    bundle_root = Path(bundle_root)
    os.environ["TCL_LIBRARY"] = str(bundle_root / "_tcl_data")
    os.environ["TK_LIBRARY"] = str(bundle_root / "_tk_data")
