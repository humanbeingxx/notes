# -*- coding: utf-8 -*-

import os,shutil

cur_path = os.getcwd()

for i in range(1,32):
    shutil.copy("daily/template.md", "daily/2018-10-%02d.md"% i)