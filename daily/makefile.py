# -*- coding: utf-8 -*-

import os,shutil

cur_path = os.getcwd()

for i in range(2,32):
    shutil.copy("daily/2018-03-01.md", "daily/2018-03-%02d.md" % i)