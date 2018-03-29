#! python3

import os
import pyperclip

cmd = "osascript -e 'IPv4 address of (system info)'"

for line in os.popen(cmd):
    print(line)
    pyperclip.copy(line)
    break
